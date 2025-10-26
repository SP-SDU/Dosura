package dk.sdu.dosura.data.repository

import dk.sdu.dosura.data.local.dao.CaregiverLinkDao
import dk.sdu.dosura.data.local.entity.CaregiverLink
import kotlinx.coroutines.flow.Flow
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaregiverLinkRepository @Inject constructor(
    private val linkDao: CaregiverLinkDao
) {
    fun getLinksForPatient(patientId: String): Flow<List<CaregiverLink>> {
        return linkDao.getLinksForPatient(patientId)
    }

    fun getLinksForCaregiver(caregiverId: String): Flow<List<CaregiverLink>> {
        return linkDao.getLinksForCaregiver(caregiverId)
    }

    suspend fun getLinkByCode(code: String): CaregiverLink? {
        return linkDao.getLinkByCode(code)
    }

    suspend fun insertLink(link: CaregiverLink): Long {
        return linkDao.insertLink(link)
    }

    suspend fun createLink(
        patientId: String,
        patientName: String,
        caregiverId: String,
        caregiverName: String
    ): Long {
        val linkCode = generateLinkCode()
        val link = CaregiverLink(
            patientId = patientId,
            caregiverId = caregiverId,
            patientName = patientName,
            caregiverName = caregiverName,
            linkCode = linkCode,
            approvedAt = System.currentTimeMillis()
        )
        return linkDao.insertLink(link)
    }

    suspend fun approveLink(linkId: Long, link: CaregiverLink) {
        linkDao.updateLink(
            link.copy(
                isActive = true,
                approvedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deactivateLink(id: Long) {
        linkDao.deactivateLink(id)
    }

    private fun generateLinkCode(): String {
        return (100000..999999).random().toString()
    }
}
