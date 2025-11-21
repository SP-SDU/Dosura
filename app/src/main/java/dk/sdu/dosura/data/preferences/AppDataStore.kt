package dk.sdu.dosura.data.preferences

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

// Centralized DataStore extension property for the app
val Context.dataStore by preferencesDataStore(name = "dosura_preferences")
