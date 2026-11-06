package app.cinderblock.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.cinderblock.CinderViewModel
import app.cinderblock.domain.ActionResult
import app.cinderblock.ui.components.DisplayText
import app.cinderblock.ui.components.Mono
import app.cinderblock.ui.components.PrimaryButton
import app.cinderblock.ui.components.ScreenColumn
import app.cinderblock.ui.nav.Routes
import app.cinderblock.ui.theme.LocalColors
import kotlinx.coroutines.launch

private enum class AuthMode { SignIn, Register, Reset }

@Composable
fun LoginScreen(nav: NavHostController, vm: CinderViewModel) {
    val state by vm.controller.state.collectAsState()
    val colors = LocalColors.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(AuthMode.SignIn) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val recovery = state.recoveryAccessToken != null

    LaunchedEffect(Unit) { vm.controller.clearSkip() }
    LaunchedEffect(state.auth, recovery) {
        if (state.auth != null && !recovery) nav.navigate(Routes.Home) { popUpTo(Routes.Home) { inclusive = true } }
    }

    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding().padding(20.dp)) {
        ScreenColumn {
            DisplayText("CINDERBLOCK", size = 28.sp)
            if (!state.cloud.configured) {
                SpacerBlock()
                DisplayText("Data stays on this device", size = 18.sp)
                Mono("Cloud sync is not configured. Training logs, runs, metrics, and settings stay on this phone.")
                PrimaryButton("Continue without signing in", {
                    scope.launch {
                        vm.controller.skipLogin()
                        nav.navigate(Routes.Home) { popUpTo(Routes.Home) { inclusive = true } }
                    }
                })
            } else if (recovery) {
                DisplayText("Set new password", size = 20.sp)
                PasswordField("New password", password) { password = it }
                PasswordField("Confirm", confirm) { confirm = it }
                Message(error, info)
                PrimaryButton("Update password", {
                    scope.launch {
                        when (val result = vm.controller.updatePassword(password, confirm)) {
                            is ActionResult.Error -> { error = result.message; info = null }
                            else -> { error = null }
                        }
                    }
                }, loading = state.authBusy)
            } else {
                DisplayText("Optional cloud sync", size = 18.sp)
                Mono("An account backs up settings, the active plan, and training logs. You can keep using Cinderblock without one.")
                if (mode != AuthMode.Reset) {
                    OutlinedTextField(email, { email = it }, label = { Mono("Email") }, modifier = Modifier.padding(top = 12.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    PasswordField("Password", password) { password = it }
                } else {
                    OutlinedTextField(email, { email = it }, label = { Mono("Email") }, modifier = Modifier.padding(top = 12.dp), singleLine = true)
                }
                Message(error, info)
                PrimaryButton(
                    when (mode) {
                        AuthMode.SignIn -> "Sign in"
                        AuthMode.Register -> "Create account"
                        AuthMode.Reset -> "Send reset link"
                    },
                    {
                        scope.launch {
                            val result = when (mode) {
                                AuthMode.SignIn -> vm.controller.signIn(email, password)
                                AuthMode.Register -> vm.controller.register(email, password)
                                AuthMode.Reset -> vm.controller.sendReset(email)
                            }
                            when (result) {
                                is ActionResult.Error -> { error = result.message; info = null }
                                is ActionResult.Info -> { info = result.message; error = null }
                                ActionResult.Ok -> { error = null; info = null }
                            }
                        }
                    },
                    loading = state.authBusy,
                )
                if (mode == AuthMode.SignIn) {
                    TextButton({ mode = AuthMode.Reset; error = null; info = null }) { Mono("Forgot password?", color = colors.neonOrange) }
                    TextButton({ mode = AuthMode.Register; error = null; info = null }) { Mono("Create account", color = colors.foreground) }
                } else {
                    TextButton({ mode = AuthMode.SignIn; error = null; info = null }) { Mono("Back to sign in", color = colors.neonOrange) }
                }
                TextButton({
                    scope.launch {
                        vm.controller.skipLogin()
                        nav.navigate(Routes.Home) { popUpTo(Routes.Home) { inclusive = true } }
                    }
                }) { Mono("Continue without signing in", color = colors.mutedForeground) }
            }
        }
    }
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value,
        onChange,
        label = { Mono(label) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun Message(error: String?, info: String?) {
    val colors = LocalColors.current
    if (error != null) Mono(error, color = colors.destructive, size = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
    if (info != null) Mono(info, color = colors.neonYellow, size = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun SpacerBlock() {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {}
}
