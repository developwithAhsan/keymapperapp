package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.data.AppDatabase
import com.example.data.ProfileRepository
import com.example.ui.KeymapperDashboard
import com.example.ui.KeymapperViewModel
import com.example.ui.ViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Instantiate Room DB, Dao and Repository
    val database = AppDatabase.getDatabase(this)
    val dao = database.profileDao()
    val repository = ProfileRepository(dao)

    // Instantiate companion VM
    val viewModelFactory = ViewModelFactory(repository)
    
    setContent {
      MyApplicationTheme {
        // Fast dynamic VM lookup
        val viewModel: KeymapperViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
          factory = viewModelFactory
        )
        
        KeymapperDashboard(
          viewModel = viewModel,
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }
}
