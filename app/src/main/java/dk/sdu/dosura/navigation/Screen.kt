package dk.sdu.dosura.navigation

sealed class Screen(val route: String) {
    // Onboarding
    object Welcome : Screen("welcome")
    object Permissions : Screen("permissions")
    object RoleSelection : Screen("role_selection")
    
    // Patient Screens
    object PatientHome : Screen("patient_home")
    object AddMedication : Screen("add_medication")
    object MedicationDetail : Screen("medication_detail/{medicationId}") {
        fun createRoute(medicationId: Long) = "medication_detail/$medicationId"
    }
    object PatientSettings : Screen("patient_settings")
    object LinkCaregiver : Screen("link_caregiver")
    
    // Caregiver Screens
    object CaregiverHome : Screen("caregiver_home")
    object LinkPatient : Screen("link_patient")
    object PatientDetail : Screen("patient_detail/{patientId}") {
        fun createRoute(patientId: String) = "patient_detail/$patientId"
    }
    object SendMessage : Screen("send_message/{patientId}") {
        fun createRoute(patientId: String) = "send_message/$patientId"
    }
    object CaregiverSettings : Screen("caregiver_settings")
}
