package dk.sdu.dosura.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dk.sdu.dosura.data.local.dao.*
import dk.sdu.dosura.data.local.entity.*

@Database(
    entities = [
        Medication::class,
        MedicationLog::class,
        CaregiverLink::class,
        MotivationalMessage::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class DosuraDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationLogDao(): MedicationLogDao
    abstract fun caregiverLinkDao(): CaregiverLinkDao
    abstract fun motivationalMessageDao(): MotivationalMessageDao
}
