package id.waspadai.app.feature.auth.presentation

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import id.waspadai.app.core.ui.BrandBlue
import id.waspadai.app.core.ui.Ink
import kotlinx.coroutines.launch

@Composable
fun AuthLandingScreen(
    onAuthenticate: suspend (email: String, password: String, isSignUp: Boolean) -> Result<Unit>,
    onAuthenticated: () -> Unit,
) {
    var isSignUp by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        error = when {
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Masukkan alamat email yang valid."
            password.length < 8 -> "Kata sandi minimal terdiri dari 8 karakter."
            isSignUp && password != confirmation -> "Konfirmasi kata sandi belum sama."
            else -> null
        }
        if (error != null || isSubmitting) return
        scope.launch {
            isSubmitting = true
            val result = onAuthenticate(email.trim(), password, isSignUp)
            isSubmitting = false
            result
                .onSuccess { onAuthenticated() }
                .onFailure { error = it.message ?: "Autentikasi belum berhasil. Coba lagi." }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Periksa dengan lebih tenang",
                    color = Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "Masuk untuk menyimpan dan melanjutkan pemeriksaan informasi Anda.",
                    modifier = Modifier.padding(top = 6.dp),
                    color = Color(0xFF557383),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        AuthModeButton(
                            text = "Masuk",
                            selected = !isSignUp,
                            onClick = { if (!isSubmitting) { isSignUp = false; error = null } },
                            modifier = Modifier.weight(1f),
                        )
                        AuthModeButton(
                            text = "Daftar",
                            selected = isSignUp,
                            onClick = { if (!isSubmitting) { isSignUp = true; error = null } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = if (isSignUp) "Buat akun" else "Selamat datang kembali",
                        color = Ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    AuthField(
                        value = email,
                        onValueChange = { if (!isSubmitting) email = it },
                        label = "Email",
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    )
                    AuthField(
                        value = password,
                        onValueChange = { if (!isSubmitting) password = it },
                        label = "Kata sandi",
                        imeAction = if (isSignUp) ImeAction.Next else ImeAction.Done,
                        isPassword = true,
                    )
                    if (isSignUp) {
                        AuthField(
                            value = confirmation,
                            onValueChange = { if (!isSubmitting) confirmation = it },
                            label = "Konfirmasi kata sandi",
                            imeAction = ImeAction.Done,
                            isPassword = true,
                        )
                    }
                    error?.let {
                        Text(it, color = Color(0xFFD82A0C), fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    Button(
                        onClick = ::submit,
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    ) {
                        Text(
                            when {
                                isSubmitting -> "Menghubungkan..."
                                isSignUp -> "Buat akun"
                                else -> "Masuk"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Text(
                text = "Dengan melanjutkan, Anda menyetujui penggunaan aplikasi WaspadAI.",
                color = Color(0xFF71808A),
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun AuthModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = if (selected) BrandBlue else Color(0xFF71808A)),
    ) {
        Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        shape = RoundedCornerShape(14.dp),
    )
}
