package dk.sdu.dosura.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dk.sdu.dosura.data.local.DosuraDatabase
import dk.sdu.dosura.data.local.dao.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DosuraDatabase {
        return Room.databaseBuilder(
            context,
            DosuraDatabase::class.java,
            "dosura_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideMedicationDao(database: DosuraDatabase): MedicationDao {
        return database.medicationDao()
    }

    @Provides
    @Singleton
    fun provideMedicationLogDao(database: DosuraDatabase): MedicationLogDao {
        return database.medicationLogDao()
    }

    @Provides
    @Singleton
    fun provideCaregiverLinkDao(database: DosuraDatabase): CaregiverLinkDao {
        return database.caregiverLinkDao()
    }

    @Provides
    @Singleton
    fun provideMotivationalMessageDao(database: DosuraDatabase): MotivationalMessageDao {
        return database.motivationalMessageDao()
    }
}
