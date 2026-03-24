package co.pilot.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import co.pilot.sample.screen.ItemDetailScreen
import co.pilot.sample.screen.ItemListScreen
import co.pilot.sample.screen.LoginScreen

class SampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "login") {
                    composable("login") {
                        LoginScreen(
                            onLoginSuccess = { username ->
                                navController.navigate("list/$username") {
                                    popUpTo("login") { inclusive = true }
                                }
                            },
                        )
                    }

                    composable(
                        route = "list/{username}",
                        arguments = listOf(navArgument("username") { type = NavType.StringType }),
                    ) { backStackEntry ->
                        val username = backStackEntry.arguments?.getString("username").orEmpty()
                        ItemListScreen(
                            username = username,
                            onItemClick = { itemId ->
                                navController.navigate("detail/$itemId")
                            },
                        )
                    }

                    composable(
                        route = "detail/{itemId}",
                        arguments = listOf(navArgument("itemId") { type = NavType.IntType }),
                    ) { backStackEntry ->
                        val itemId = backStackEntry.arguments?.getInt("itemId") ?: 0
                        ItemDetailScreen(
                            itemId = itemId,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
