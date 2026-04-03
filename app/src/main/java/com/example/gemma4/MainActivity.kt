package com.example.gemma4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gemma4.ui.theme.Gemma4ChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Gemma4ChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Gemma4ChatApp()
                }
            }
        }
    }
}

@Composable
fun Gemma4ChatApp() {
    val navController = rememberNavController()
    val chatViewModel: ChatViewModel = viewModel()

    NavHost(navController = navController, startDestination = "settings") {
        composable("settings") {
            SettingsScreen(
                currentSettings = chatViewModel.settings,
                currentModel = chatViewModel.modelType,
                onSave = { newSettings, reloadModel ->
                    if (!chatViewModel.isModelLoaded) {
                        chatViewModel.settings = newSettings
                        chatViewModel.loadModel(chatViewModel.modelType)
                    } else {
                        chatViewModel.updateSettings(newSettings, reloadModel)
                    }
                    navController.navigate("chat") {
                        popUpTo("settings") { inclusive = true }
                    }
                },
                onBack = {
                    if (chatViewModel.isModelLoaded) {
                        navController.navigate("chat") {
                            popUpTo("settings") { inclusive = true }
                        }
                    }
                },
                onDownload = { modelType ->
                    navController.navigate("download/${modelType.name}")
                }
            )
        }

        composable("download/{modelName}") { backStackEntry ->
            val modelName = backStackEntry.arguments?.getString("modelName") ?: return@composable
            val modelType = ModelType.valueOf(modelName)

            DownloadScreen(
                modelType = modelType,
                onDownloadComplete = {
                    navController.popBackStack()
                }
            )
        }

        composable("chat") {
            ChatScreen(
                chatViewModel = chatViewModel,
                onOpenSettings = {
                    navController.navigate("settings")
                }
            )
        }
    }
}
