package dk.sdu.dosura

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dk.sdu.dosura.data.local.DosuraDatabase
import dk.sdu.dosura.data.local.entity.CaregiverLink
import dk.sdu.dosura.data.repository.CaregiverLinkRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CaregiverLinkingTest {

    private lateinit var database: DosuraDatabase
    private lateinit var repository: CaregiverLinkRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            DosuraDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = CaregiverLinkRepository(database.caregiverLinkDao())
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testPeerToManyCaregiversCanLinkToSamePatient() = runBlocking {
        val patientId = "patient_001"
        val patientName = "John Doe"

        val linkId1 = repository.createLink(
            patientId = patientId,
            patientName = patientName,
            caregiverId = "caregiver_001",
            caregiverName = "Mary Smith"
        )

        val linkId2 = repository.createLink(
            patientId = patientId,
            patientName = patientName,
            caregiverId = "caregiver_002",
            caregiverName = "Bob Johnson"
        )

        val linkId3 = repository.createLink(
            patientId = patientId,
            patientName = patientName,
            caregiverId = "caregiver_003",
            caregiverName = "Alice Williams"
        )

        val linksForPatient = repository.getLinksForPatient(patientId).first()
        
        assertEquals(3, linksForPatient.size)
        assertTrue(linksForPatient.any { it.caregiverId == "caregiver_001" })
        assertTrue(linksForPatient.any { it.caregiverId == "caregiver_002" })
        assertTrue(linksForPatient.any { it.caregiverId == "caregiver_003" })
    }

    @Test
    fun testCaregiversCanSeePatientsLinkedToThem() = runBlocking {
        val caregiverId = "caregiver_001"

        repository.createLink(
            patientId = "patient_001",
            patientName = "John Doe",
            caregiverId = caregiverId,
            caregiverName = "Mary Smith"
        )

        repository.createLink(
            patientId = "patient_002",
            patientName = "Jane Doe",
            caregiverId = caregiverId,
            caregiverName = "Mary Smith"
        )

        val linksForCaregiver = repository.getLinksForCaregiver(caregiverId).first()
        
        assertEquals(2, linksForCaregiver.size)
        assertTrue(linksForCaregiver.any { it.patientId == "patient_001" })
        assertTrue(linksForCaregiver.any { it.patientId == "patient_002" })
    }

    @Test
    fun testLinkCodeIsUnique() = runBlocking {
        val link1 = repository.createLink(
            patientId = "patient_001",
            patientName = "John Doe",
            caregiverId = "caregiver_001",
            caregiverName = "Mary Smith"
        )

        val link2 = repository.createLink(
            patientId = "patient_002",
            patientName = "Jane Doe",
            caregiverId = "caregiver_002",
            caregiverName = "Bob Johnson"
        )

        val allLinks = repository.getLinksForPatient("patient_001").first() +
                repository.getLinksForPatient("patient_002").first()
        
        val linkCodes = allLinks.map { it.linkCode }
        assertEquals(linkCodes.size, linkCodes.distinct().size)
    }

    @Test
    fun testDeactivateLinkRemovesFromActiveLinks() = runBlocking {
        val linkId = repository.createLink(
            patientId = "patient_001",
            patientName = "John Doe",
            caregiverId = "caregiver_001",
            caregiverName = "Mary Smith"
        )

        var links = repository.getLinksForPatient("patient_001").first()
        assertEquals(1, links.size)

        repository.deactivateLink(linkId)

        links = repository.getLinksForPatient("patient_001").first()
        assertEquals(0, links.size)
    }

    @Test
    fun testLinkByCodeRetrievesCorrectLink() = runBlocking {
        val patientId = "patient_001"
        val caregiverId = "caregiver_001"
        
        repository.createLink(
            patientId = patientId,
            patientName = "John Doe",
            caregiverId = caregiverId,
            caregiverName = "Mary Smith"
        )

        val links = repository.getLinksForPatient(patientId).first()
        val linkCode = links.first().linkCode

        val retrievedLink = repository.getLinkByCode(linkCode)
        
        assertNotNull(retrievedLink)
        assertEquals(patientId, retrievedLink?.patientId)
        assertEquals(caregiverId, retrievedLink?.caregiverId)
    }
}
