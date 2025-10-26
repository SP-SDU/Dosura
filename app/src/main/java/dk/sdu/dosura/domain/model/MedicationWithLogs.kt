package dk.sdu.dosura.domain.model

import dk.sdu.dosura.data.local.entity.Medication
import dk.sdu.dosura.data.local.entity.MedicationLog

data class MedicationWithLogs(
    val medication: Medication,
    val recentLogs: List<MedicationLog>
)
