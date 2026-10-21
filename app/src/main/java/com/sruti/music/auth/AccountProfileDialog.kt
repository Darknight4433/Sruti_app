package com.sruti.music.auth

import android.accounts.AccountManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.sruti.music.components.GlassBadge
import com.sruti.music.theme.GlassWhite
import com.sruti.music.theme.NeonPurple
import kotlinx.coroutines.launch

@Composable
fun AccountProfileDialog(
    userProfile: UserProfile = UserProfile(),
    deviceTracksCount: Int = 0,
    favoriteTracksCount: Int = 0,
    playlistsCount: Int = 0,
    onOpenSettings: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val authRepo = remember { FirebaseAuthRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    val currentProfile by authRepo.userProfile.collectAsState()
    val activeProfile = if (currentProfile.isAuthenticated) currentProfile else userProfile

    val prefs = remember { context.getSharedPreferences("sruti_auth_prefs", Context.MODE_PRIVATE) }
    var webClientId by remember {
        val detected = runCatching {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) context.getString(resId) else null
        }.getOrNull()
        val saved = prefs.getString("custom_web_client_id", null)
        mutableStateOf(saved ?: detected ?: "")
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    var isEditingName by remember { mutableStateOf(false) }
    var editNameText by remember(activeProfile.displayName) { mutableStateOf(activeProfile.displayName ?: "") }

    var showEmailForm by remember { mutableStateOf(false) }
    var isSignUpMode by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var displayNameInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showDirectGoogleEmail by remember { mutableStateOf(false) }
    var googleEmailInput by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                authRepo.updatePhotoUrl(uri.toString())
            }
        }
    }

    // 1. Native Android AccountManager Chooser launcher (Native OS Dialog, bypasses SHA-1 requirement)
    val accountChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLoading = false
        android.util.Log.d("GoogleSignInDebug", "accountChooserLauncher result: resultCode=${result.resultCode}, data=${result.data}")
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val selectedEmail = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            android.util.Log.d("GoogleSignInDebug", "AccountManager chosen account: $selectedEmail")
            if (!selectedEmail.isNullOrBlank()) {
                val lastAccount = runCatching { GoogleSignIn.getLastSignedInAccount(context) }.getOrNull()
                val name = if (lastAccount?.email.equals(selectedEmail, ignoreCase = true)) lastAccount?.displayName else null
                val photo = if (lastAccount?.email.equals(selectedEmail, ignoreCase = true)) lastAccount?.photoUrl?.toString() else null
                authRepo.setGoogleAccount(email = selectedEmail, displayName = name, photoUrl = photo)
                errorMessage = null
            } else {
                errorMessage = "No account selected"
            }
        }
    }

    val activity = context as? android.app.Activity

    // 2. Google Play Services OAuth Launcher (Used for full Firebase credential verification)
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        android.util.Log.d("GoogleSignInDebug", "Launcher result received: resultCode=${result.resultCode}, data=${result.data}")
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            android.util.Log.d("GoogleSignInDebug", "Account result: name=${account?.displayName}, email=${account?.email}, idToken=${account?.idToken != null}")
            if (account != null) {
                val idToken = account.idToken
                if (!idToken.isNullOrBlank()) {
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        val authRes = authRepo.signInWithGoogle(idToken)
                        isLoading = false
                        authRes.onSuccess {
                            android.util.Log.d("GoogleSignInDebug", "Firebase auth succeeded: ${it.displayName}")
                        }.onFailure {
                            android.util.Log.w("GoogleSignInDebug", "Firebase auth failed (${it.message}), saving verified Google account directly", it)
                            authRepo.setGoogleProfile(account)
                        }
                    }
                } else {
                    authRepo.setGoogleProfile(account)
                    isLoading = false
                }
            } else {
                isLoading = false
            }
        } catch (e: ApiException) {
            android.util.Log.e("GoogleSignInDebug", "ApiException caught: statusCode=${e.statusCode}, message=${e.message}", e)
            isLoading = false
            // If code 10 (DEVELOPER_ERROR) or 12500, attempt Firebase OAuth web flow if activity is present
            if ((e.statusCode == 10 || e.statusCode == 12500 || e.statusCode == 12501) && activity != null) {
                isLoading = true
                scope.launch {
                    val oauthRes = authRepo.signInWithGoogleProvider(activity)
                    isLoading = false
                    oauthRes.onSuccess {
                        errorMessage = null
                    }.onFailure { providerErr ->
                        android.util.Log.w("GoogleSignInDebug", "signInWithGoogleProvider failed: ${providerErr.message}, launching AccountManager", providerErr)
                        try {
                            val intent = AccountManager.newChooseAccountIntent(
                                null, null, arrayOf("com.google"), null, null, null, null
                            )
                            accountChooserLauncher.launch(intent)
                        } catch (retryEx: Exception) {
                            errorMessage = "Sign in: ${providerErr.localizedMessage ?: "Please choose an account"}"
                        }
                    }
                }
            } else if (e.statusCode == 10 || e.statusCode == 12500 || e.statusCode == 12501) {
                try {
                    val intent = AccountManager.newChooseAccountIntent(
                        null, null, arrayOf("com.google"), null, null, null, null
                    )
                    accountChooserLauncher.launch(intent)
                } catch (retryEx: Exception) {
                    errorMessage = "Google Sign-In: Add SHA-1 to Firebase (Code ${e.statusCode})"
                }
            } else {
                errorMessage = "Google Sign-In (${e.statusCode}): ${e.message ?: "Sign-in failed"}"
            }
        } catch (e: Exception) {
            android.util.Log.e("GoogleSignInDebug", "Generic exception in GoogleSignIn: ${e.message}", e)
            isLoading = false
            errorMessage = "Google Sign-In failed: ${e.message}"
        }
    }

    fun launchGoogleAccountFlow() {
        isLoading = true
        errorMessage = null
        try {
            val intent = AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                null,
                null,
                null,
                null
            )
            accountChooserLauncher.launch(intent)
        } catch (e: Exception) {
            android.util.Log.w("GoogleSignInDebug", "AccountManager chooser failed: ${e.message}", e)
            isLoading = false
            errorMessage = "Could not open Google account picker: ${e.message}"
        }
    }

    fun launchGoogleSignIn(forceBasic: Boolean = false) {
        isLoading = true
        errorMessage = null
        try {
            val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()

            val trimmedClient = webClientId.trim()
            if (!forceBasic && trimmedClient.isNotEmpty()) {
                gsoBuilder.requestIdToken(trimmedClient)
            }

            val client = GoogleSignIn.getClient(context, gsoBuilder.build())
            googleSignInLauncher.launch(client.signInIntent)
        } catch (e: Exception) {
            android.util.Log.e("GoogleSignInDebug", "launchGoogleSignIn setup error: ${e.message}", e)
            if (activity != null) {
                scope.launch {
                    authRepo.signInWithGoogleProvider(activity)
                        .onSuccess { isLoading = false }
                        .onFailure {
                            isLoading = false
                            launchGoogleAccountFlow()
                        }
                }
            } else {
                isLoading = false
                launchGoogleAccountFlow()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xEE201538),
                            Color(0xFA0F0B1E)
                        )
                    )
                )
                .border(1.dp, Color(0x35FFFFFF), RoundedCornerShape(26.dp))
                .padding(22.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = GlassWhite
                        )
                    }
                }

                if (activeProfile.isAuthenticated) {
                    // ==========================================
                    // AUTHENTICATED USER STATE
                    // ==========================================

                    // Profile Avatar (Real photo or stylized monogram) with tap-to-change photo support
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .clickable { photoPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!activeProfile.photoUrl.isNullOrBlank()) {
                            val painter = rememberAsyncImagePainter(model = activeProfile.photoUrl)
                            Image(
                                painter = painter,
                                contentDescription = activeProfile.displayName ?: "Profile Photo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .border(2.5.dp, NeonPurple, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            // Monogram Initial Fallback
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF8A2BE2), NeonPurple)
                                        )
                                    )
                                    .border(2.dp, Color(0x60FFFFFF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = activeProfile.initials,
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        // Camera badge on bottom right
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(NeonPurple)
                                .border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "Change photo",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Tap photo to change",
                        color = GlassWhite.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Display Name & Inline Edit
                    if (isEditingName) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            OutlinedTextField(
                                value = editNameText,
                                onValueChange = { editNameText = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = NeonPurple,
                                    unfocusedBorderColor = Color(0x40FFFFFF)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        if (editNameText.isNotBlank()) {
                                            authRepo.updateDisplayName(editNameText)
                                        }
                                        isEditingName = false
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = "Save", tint = NeonPurple)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = activeProfile.displayName?.ifBlank { "Music Lover" } ?: "Music Lover",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    editNameText = activeProfile.displayName ?: ""
                                    isEditingName = true
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Edit Name",
                                    tint = GlassWhite.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = activeProfile.email ?: (if (activeProfile.isAnonymous) "Guest Account" else "Connected"),
                        color = NeonPurple,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    GlassBadge(
                        text = when {
                            activeProfile.isAnonymous -> "Guest Mode"
                            activeProfile.providerId.contains("google", ignoreCase = true) -> "Google Account"
                            else -> "Firebase Verified"
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Authentic Statistics Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(label = "Local Songs", value = "$deviceTracksCount", modifier = Modifier.weight(1f))
                        StatCard(label = "Favorites", value = "$favoriteTracksCount", modifier = Modifier.weight(1f), isPink = true)
                        StatCard(label = "Playlists", value = "$playlistsCount", modifier = Modifier.weight(1f))
                    }

                    if (onOpenSettings != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x25BA2DF7))
                                .border(1.dp, NeonPurple.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                .clickable {
                                    onDismiss()
                                    onOpenSettings()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Tune,
                                        contentDescription = "Appearance",
                                        tint = NeonPurple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Appearance & Background",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Adaptive thumbnail, styles & themes",
                                            color = GlassWhite.copy(alpha = 0.70f),
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = GlassWhite.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Sign Out Action Button
                    Button(
                        onClick = {
                            authRepo.signOut()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x30FFFFFF)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(14.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Logout,
                            contentDescription = "Sign Out",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }

                } else {
                    // ==========================================
                    // SIGNED-OUT STATE (Google Sign-In Primary)
                    // ==========================================

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color(0x20FFFFFF))
                            .border(1.5.dp, NeonPurple, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PersonOutline,
                            contentDescription = "Guest",
                            tint = NeonPurple,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Sign In to Sruti",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Sign in with your Google account to sync playlists, favorites & history.",
                        color = GlassWhite.copy(alpha = 0.75f),
                        fontSize = 12.5.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // PRIMARY ACTION: Universal Sign in with Google Button
                    Button(
                        onClick = { launchGoogleSignIn() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color(0xFF1E1338),
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "G",
                                    color = Color(0xFF4285F4),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Sign in with Google",
                                    color = Color(0xFF1E1338),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Direct Google Email Option (for any user wanting direct email authentication)
                    TextButton(
                        onClick = { showDirectGoogleEmail = !showDirectGoogleEmail },
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = if (showDirectGoogleEmail) "Hide Direct Google Email" else "Or enter Google email directly",
                            color = NeonPurple,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (showDirectGoogleEmail) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = googleEmailInput,
                                onValueChange = { googleEmailInput = it },
                                placeholder = { Text("youremail@gmail.com", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = NeonPurple,
                                    unfocusedBorderColor = Color(0x35FFFFFF)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (googleEmailInput.contains("@")) {
                                        authRepo.setGoogleAccount(email = googleEmailInput.trim())
                                        showDirectGoogleEmail = false
                                    } else {
                                        errorMessage = "Please enter a valid Google email"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(50.dp)
                            ) {
                                Text("Go", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary: Continue as Guest
                    OutlinedButton(
                        onClick = {
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                authRepo.signInAnonymously().onFailure {
                                    errorMessage = it.localizedMessage ?: "Guest login failed"
                                }
                                isLoading = false
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x35FFFFFF)),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text("Continue as Guest", color = Color.White, fontSize = 13.5.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Email/Password accordion (expandable if needed)
                    TextButton(onClick = { showEmailForm = !showEmailForm }) {
                        Text(
                            text = if (showEmailForm) "Hide Email Sign-In" else "Or sign in with email",
                            color = GlassWhite.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }

                    if (showEmailForm) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (isSignUpMode) {
                            OutlinedTextField(
                                value = displayNameInput,
                                onValueChange = { displayNameInput = it },
                                label = { Text("Display Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = NeonPurple,
                                    unfocusedBorderColor = Color(0x35FFFFFF)
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = NeonPurple,
                                unfocusedBorderColor = Color(0x35FFFFFF)
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                        contentDescription = "Toggle password",
                                        tint = GlassWhite.copy(alpha = 0.6f)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = NeonPurple,
                                unfocusedBorderColor = Color(0x35FFFFFF)
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                if (emailInput.isBlank() || passwordInput.isBlank()) {
                                    errorMessage = "Please enter both email and password"
                                    return@Button
                                }
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    val result = if (isSignUpMode) {
                                        authRepo.signUpWithEmail(emailInput, passwordInput, displayNameInput)
                                    } else {
                                        authRepo.signInWithEmail(emailInput, passwordInput)
                                    }
                                    isLoading = false
                                    result.onFailure {
                                        errorMessage = it.localizedMessage ?: "Authentication failed"
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Text(text = if (isSignUpMode) "Create Account" else "Sign In with Email", color = Color.White)
                        }

                        TextButton(onClick = { isSignUpMode = !isSignUpMode }) {
                            Text(
                                text = if (isSignUpMode) "Already have an account? Sign In" else "Don't have an account? Sign Up",
                                color = NeonPurple,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isPink: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x18FFFFFF))
            .border(1.dp, Color(0x25FFFFFF), RoundedCornerShape(16.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                color = if (isPink) Color(0xFFFF2A85) else Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = GlassWhite.copy(alpha = 0.7f),
                fontSize = 10.5.sp,
                maxLines = 1
            )
        }
    }
}
