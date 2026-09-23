package id.waspadai.app.feature.auth.presentation

import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.waspadai.app.R
import id.waspadai.app.core.ui.BrandBlue
import id.waspadai.app.feature.auth.data.RememberedCredentials
import kotlinx.coroutines.launch

private enum class AuthStep {
    Login,
    Register,
    ForgotEmail,
    RecoveryCode,
    NewPassword,
}

@Composable
fun AuthLandingScreen(
    onAuthenticate: suspend (email: String, password: String, isSignUp: Boolean) -> Result<Unit>,
    onAuthenticated: () -> Unit,
    rememberedCredentials: RememberedCredentials? = null,
    onRememberCredentials: (email: String, password: String) -> Unit = { _, _ -> },
    onForgetCredentials: () -> Unit = {},
    onRequestPasswordReset: suspend (email: String) -> Result<Unit> = { Result.success(Unit) },
    onVerifyPasswordResetCode: suspend (email: String, code: String) -> Result<Unit> = { _, _ -> Result.success(Unit) },
    onUpdatePassword: suspend (newPassword: String) -> Result<Unit> = { Result.success(Unit) },
) {
    var step by rememberSaveable { mutableStateOf(AuthStep.Login) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var recoveryCode by rememberSaveable { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newPasswordConfirmation by remember { mutableStateOf("") }
    var rememberMe by rememberSaveable { mutableStateOf(rememberedCredentials != null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val horizontalGutter = if (configuration.screenWidthDp < 360) 18.dp else 24.dp

    LaunchedEffect(rememberedCredentials) {
        rememberedCredentials?.let { credentials ->
            email = credentials.email
            password = credentials.password
            rememberMe = true
        }
    }

    fun showStep(target: AuthStep) {
        if (isSubmitting) return
        step = target
        error = null
        notice = null
        confirmation = ""
    }

    fun submit() {
        if (isSubmitting) return
        val normalizedEmail = email.trim()
        error = when (step) {
            AuthStep.Login, AuthStep.Register, AuthStep.ForgotEmail -> when {
                !Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches() -> "Masukkan alamat email yang valid."
                step != AuthStep.ForgotEmail && password.length < 8 -> "Kata sandi minimal terdiri dari 8 karakter."
                step == AuthStep.Register && password != confirmation -> "Konfirmasi kata sandi belum sama."
                else -> null
            }
            AuthStep.RecoveryCode -> if (recoveryCode.trim().length !in 6..8) "Masukkan kode dari email dengan lengkap." else null
            AuthStep.NewPassword -> when {
                newPassword.length < 8 -> "Kata sandi baru minimal terdiri dari 8 karakter."
                newPassword != newPasswordConfirmation -> "Konfirmasi kata sandi baru belum sama."
                else -> null
            }
        }
        if (error != null) return

        scope.launch {
            isSubmitting = true
            notice = null
            val result = when (step) {
                AuthStep.Login -> onAuthenticate(normalizedEmail, password, false)
                AuthStep.Register -> onAuthenticate(normalizedEmail, password, true)
                AuthStep.ForgotEmail -> onRequestPasswordReset(normalizedEmail)
                AuthStep.RecoveryCode -> onVerifyPasswordResetCode(normalizedEmail, recoveryCode.trim())
                AuthStep.NewPassword -> onUpdatePassword(newPassword)
            }
            isSubmitting = false
            result.onSuccess {
                when (step) {
                    AuthStep.Login -> {
                        if (rememberMe) onRememberCredentials(normalizedEmail, password) else onForgetCredentials()
                        onAuthenticated()
                    }
                    AuthStep.Register -> onAuthenticated()
                    AuthStep.ForgotEmail -> {
                        step = AuthStep.RecoveryCode
                        notice = "Kode pemulihan telah dikirim ke $normalizedEmail."
                    }
                    AuthStep.RecoveryCode -> {
                        step = AuthStep.NewPassword
                        notice = "Email berhasil diverifikasi. Silakan buat kata sandi baru."
                    }
                    AuthStep.NewPassword -> {
                        onForgetCredentials()
                        password = ""
                        newPassword = ""
                        newPasswordConfirmation = ""
                        recoveryCode = ""
                        step = AuthStep.Login
                        notice = "Kata sandi berhasil diperbarui. Silakan masuk kembali."
                    }
                }
            }.onFailure {
                error = it.message ?: "Permintaan belum berhasil. Silakan coba lagi."
            }
        }
    }

    Box(Modifier.fillMaxSize().background(AuthBackdrop)) {
        Image(
            painter = painterResource(R.drawable.community_header_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = .22f,
            modifier = Modifier.matchParentSize(),
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            AuthHero(step)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-54).dp)
                    .padding(horizontal = horizontalGutter),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AuthCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                ) {
                    AuthForm(
                        step = step,
                        email = email,
                        onEmailChange = { if (!isSubmitting) email = it },
                        password = password,
                        onPasswordChange = { if (!isSubmitting) password = it },
                        confirmation = confirmation,
                        onConfirmationChange = { if (!isSubmitting) confirmation = it },
                        recoveryCode = recoveryCode,
                        onRecoveryCodeChange = {
                            if (!isSubmitting && it.length <= 8 && it.all(Char::isDigit)) recoveryCode = it
                        },
                        newPassword = newPassword,
                        onNewPasswordChange = { if (!isSubmitting) newPassword = it },
                        newPasswordConfirmation = newPasswordConfirmation,
                        onNewPasswordConfirmationChange = { if (!isSubmitting) newPasswordConfirmation = it },
                        rememberMe = rememberMe,
                        onRememberMeChange = { if (!isSubmitting) rememberMe = it },
                        error = error,
                        notice = notice,
                        isSubmitting = isSubmitting,
                        onSubmit = ::submit,
                        onShowStep = ::showStep,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AuthHero(step: AuthStep) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(270.dp)
            .padding(start = 34.dp, end = 34.dp, top = 82.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (step == AuthStep.Login || step == AuthStep.Register) "Halo!" else "Pulihkan akun",
            color = Color.White,
            fontSize = 42.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = if (step == AuthStep.Login || step == AuthStep.Register) {
                "Selamat datang di WaspadAI"
            } else {
                "Kami bantu Anda kembali masuk dengan aman."
            },
            color = Color.White.copy(alpha = .92f),
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun AuthForm(
    step: AuthStep,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmation: String,
    onConfirmationChange: (String) -> Unit,
    recoveryCode: String,
    onRecoveryCodeChange: (String) -> Unit,
    newPassword: String,
    onNewPasswordChange: (String) -> Unit,
    newPasswordConfirmation: String,
    onNewPasswordConfirmationChange: (String) -> Unit,
    rememberMe: Boolean,
    onRememberMeChange: (Boolean) -> Unit,
    error: String?,
    notice: String?,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
    onShowStep: (AuthStep) -> Unit,
) {
    val title = when (step) {
        AuthStep.Login -> "Masuk ke akun kamu"
        AuthStep.Register -> "Daftarkan akun kamu"
        AuthStep.ForgotEmail -> "Lupa kata sandi"
        AuthStep.RecoveryCode -> "Masukkan kode"
        AuthStep.NewPassword -> "Kata sandi baru"
    }
    val buttonText = when {
        isSubmitting -> "Mohon tunggu..."
        step == AuthStep.Login -> "Masuk"
        step == AuthStep.Register -> "Daftar"
        step == AuthStep.ForgotEmail -> "Kirim kode"
        step == AuthStep.RecoveryCode -> "Verifikasi kode"
        else -> "Simpan kata sandi"
    }

    Column(
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (step != AuthStep.Login && step != AuthStep.Register) {
            Row(
                modifier = Modifier
                    .clickable(enabled = !isSubmitting) { onShowStep(AuthStep.Login) }
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = null,
                    tint = AuthAccent,
                    modifier = Modifier.size(18.dp),
                )
                Text("Kembali ke login", color = AuthAccent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        val isAccountEntry = step == AuthStep.Login || step == AuthStep.Register
        Text(
            text = title,
            color = if (isAccountEntry) Color(0xFF465861) else AuthAccent,
            fontSize = if (isAccountEntry) 14.sp else 30.sp,
            fontWeight = if (isAccountEntry) FontWeight.Normal else FontWeight.ExtraBold,
            textAlign = if (isAccountEntry) TextAlign.Center else TextAlign.Start,
            modifier = if (isAccountEntry) {
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            } else {
                Modifier
            },
        )

        if (step == AuthStep.Login || step == AuthStep.Register || step == AuthStep.ForgotEmail) {
            AuthField(
                value = email,
                onValueChange = onEmailChange,
                placeholder = "Masukkan email",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
            )
        }

        when (step) {
            AuthStep.Login, AuthStep.Register -> AuthField(
                value = password,
                onValueChange = onPasswordChange,
                placeholder = "Masukkan kata sandi",
                isPassword = true,
                imeAction = if (step == AuthStep.Register) ImeAction.Next else ImeAction.Done,
                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
            )
            AuthStep.RecoveryCode -> {
                Text(
                    "Masukkan kode pemulihan yang dikirim ke $email.",
                    color = AuthMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                AuthField(
                    value = recoveryCode,
                    onValueChange = onRecoveryCodeChange,
                    placeholder = "Kode verifikasi",
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                    leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                )
            }
            AuthStep.NewPassword -> {
                AuthField(
                    value = newPassword,
                    onValueChange = onNewPasswordChange,
                    placeholder = "Kata sandi baru",
                    isPassword = true,
                    imeAction = ImeAction.Next,
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                )
                AuthField(
                    value = newPasswordConfirmation,
                    onValueChange = onNewPasswordConfirmationChange,
                    placeholder = "Konfirmasi kata sandi baru",
                    isPassword = true,
                    imeAction = ImeAction.Done,
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                )
            }
            AuthStep.ForgotEmail -> Text(
                "Kami akan mengirim kode pemulihan ke email tersebut.",
                color = AuthMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }

        if (step == AuthStep.Register) {
            AuthField(
                value = confirmation,
                onValueChange = onConfirmationChange,
                placeholder = "Masukkan ulang kata sandi",
                isPassword = true,
                imeAction = ImeAction.Done,
                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
            )
        }

        if (step == AuthStep.Login) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier
                        .offset(x = (-12).dp)
                        .clickable(enabled = !isSubmitting) { onRememberMeChange(!rememberMe) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = onRememberMeChange,
                        enabled = !isSubmitting,
                        colors = CheckboxDefaults.colors(checkedColor = AuthAccent),
                    )
                    Text("Ingat saya", color = AuthMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Text(
                    text = "Lupa password?",
                    color = AuthAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(enabled = !isSubmitting) { onShowStep(AuthStep.ForgotEmail) }
                        .padding(vertical = 8.dp),
                )
            }
        }

        notice?.let { Text(it, color = AuthAccent, fontSize = 12.sp, lineHeight = 17.sp) }
        error?.let { Text(it, color = Color(0xFFD9364F), fontSize = 12.sp, lineHeight = 17.sp) }

        Button(
            onClick = onSubmit,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AuthAccent),
        ) {
            Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        if (step == AuthStep.Login || step == AuthStep.Register) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (step == AuthStep.Login) "Belum punya akun?" else "Sudah punya akun?",
                    color = AuthMuted,
                    fontSize = 14.sp,
                )
                Text(
                    text = if (step == AuthStep.Login) "Daftar" else "Masuk",
                    color = AuthAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable(enabled = !isSubmitting) {
                        onShowStep(if (step == AuthStep.Login) AuthStep.Register else AuthStep.Login)
                        }
                        .padding(start = 4.dp, top = 5.dp, bottom = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    imeAction: ImeAction,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(15.dp)
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .border(1.dp, AuthFieldBorder, shape),
        placeholder = { Text(placeholder, color = Color(0xFF9AA7A7), fontSize = 13.sp) },
        leadingIcon = leadingIcon,
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (passwordVisible) "Sembunyikan kata sandi" else "Tampilkan kata sandi",
                        tint = AuthMuted,
                    )
                }
            }
        } else null,
        singleLine = true,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            disabledContainerColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedLeadingIconColor = AuthAccent,
            unfocusedLeadingIconColor = Color(0xFF9AA7A7),
            cursorColor = AuthAccent,
        ),
    )
}

private val AuthBackdrop = BrandBlue
private val AuthAccent = BrandBlue
private val AuthCard = Color(0xFFF8FAFC)
private val AuthMuted = Color(0xFF536B7A)
private val AuthFieldBorder = Color(0xFFB8C9D8)
