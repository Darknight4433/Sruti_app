    package com.sruti.music.data.db

    import android.content.Context
    import androidx.room.Database
    import androidx.room.Room
    import androidx.room.RoomDatabase

    @Database(
        entities = [
            FavoriteEntity::class,
            HistoryEntity::class,
            PlaylistEntity::class,
            PlaylistSongEntity::class,
            QueueSnapshotEntity::class,
            DownloadEntity::class
        ],
        version = 5,
        exportSchema = false
    )
    abstract class SrutiDatabase : RoomDatabase() {

        abstract fun musicDao(): MusicDao

        companion object {
            @Volatile
            private var INSTANCE: SrutiDatabase? = null

            fun getDatabase(context: Context): SrutiDatabase {
                return INSTANCE ?: synchronized(this) {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        SrutiDatabase::class.java,
                        "sruti_music.db"
                    ).fallbackToDestructiveMigration().build()
                    INSTANCE = instance
                    instance
                }
            }
        }
    }
