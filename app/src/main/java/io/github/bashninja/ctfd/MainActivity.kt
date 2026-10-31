package io.github.bashninja.ctfd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.bashninja.ctfd.ui.AccountScreen
import io.github.bashninja.ctfd.ui.AddCtfScreen
import io.github.bashninja.ctfd.ui.AppViewModel
import io.github.bashninja.ctfd.ui.ChallengeScreen
import io.github.bashninja.ctfd.ui.CtfdTheme
import io.github.bashninja.ctfd.ui.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CtfdTheme { App() } }
    }
}

@Composable
private fun App(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val ctfs by vm.store.ctfs.collectAsState()
    NavHost(nav, startDestination = if (ctfs.isEmpty()) "add" else "home") {
        composable("add") {
            AddCtfScreen(
                vm = vm,
                canGoBack = ctfs.isNotEmpty(),
                onBack = { nav.popBackStack() },
                onAdded = {
                    nav.navigate("home") { popUpTo(0) }
                },
            )
        }
        composable("home") {
            HomeScreen(
                vm = vm,
                onChallenge = { nav.navigate("challenge/$it") },
                onAccount = { type, id, name -> nav.navigate("account/$type/$id?name=${android.net.Uri.encode(name)}") },
                onAddCtf = { nav.navigate("add") },
            )
        }
        composable("challenge/{id}", listOf(navArgument("id") { type = NavType.IntType })) { entry ->
            ChallengeScreen(
                vm = vm,
                id = entry.arguments!!.getInt("id"),
                onBack = { nav.popBackStack() },
                onChallenge = { nav.navigate("challenge/$it") { popUpTo("home") } },
            )
        }
        composable(
            "account/{type}/{id}?name={name}",
            listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("id") { type = NavType.IntType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val args = entry.arguments!!
            AccountScreen(
                vm = vm,
                type = args.getString("type")!!,
                id = args.getInt("id"),
                name = args.getString("name").orEmpty(),
                onBack = { nav.popBackStack() },
                onChallenge = { nav.navigate("challenge/$it") },
            )
        }
    }
}
