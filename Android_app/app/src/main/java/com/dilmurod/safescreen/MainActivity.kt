package com.dilmurod.safescreen

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.dilmurod.safescreen.ui.theme.SafeScreenTheme

class MainActivity : ComponentActivity() {

    private val OVERLAY_PERMISSION_REQUEST = 1001
    private val SCREEN_CAPTURE_REQUEST = 2001
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onCreate")
        setContent {
            SafeScreenTheme {
                HomeScreen(
                    onStartMonitoring = { checkPermissionsAndStart() },
                    onStopMonitoring = { stopMonitoringService() }
                )
            }
        }
    }

    private fun isServiceRunning(): Boolean {
        val prefs = getSharedPreferences("SafeScreenPrefs", MODE_PRIVATE)
        return prefs.getBoolean("isServiceRunning", false)
    }

    private fun stopMonitoringService() {
        try {
            val serviceIntent = Intent(this, ScreenMonitorService::class.java).apply {
                action = "STOP_SERVICE"
            }
            stopService(serviceIntent)
            getSharedPreferences("SafeScreenPrefs", MODE_PRIVATE)
                .edit().putBoolean("isServiceRunning", false).apply()
            Log.d(TAG, "Service stopped from MainActivity")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service: ${e.message}", e)
        }
    }

    private fun checkPermissionsAndStart() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission()
            } else {
                requestScreenCapturePermission()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestOverlayPermission() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting overlay permission: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestScreenCapturePermission() {
        try {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST)
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Error requesting screen capture: ${e.message}", e)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        try {
            when (requestCode) {
                OVERLAY_PERMISSION_REQUEST -> {
                    if (Settings.canDrawOverlays(this)) {
                        requestScreenCapturePermission()
                    } else {
                        Toast.makeText(this, "Overlay ruxsati talab etiladi!", Toast.LENGTH_LONG).show()
                    }
                }
                SCREEN_CAPTURE_REQUEST -> {
                    if (resultCode == Activity.RESULT_OK && data != null) {
                        val serviceIntent = Intent(this, ScreenMonitorService::class.java).apply {
                            putExtra("resultCode", resultCode)
                            putExtra("data", data)
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }
                        getSharedPreferences("SafeScreenPrefs", MODE_PRIVATE)
                            .edit().putBoolean("isServiceRunning", true).apply()
                        Toast.makeText(this, "SafeScreen ishga tushdi ✓", Toast.LENGTH_SHORT).show()
                        finishAndRemoveTask()
                    } else {
                        Toast.makeText(this, "Yozib olish ruxsati talab etiladi!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onActivityResult: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

// ─────────────────────────────────────────────
//  Shared Prefs Helpers
// ─────────────────────────────────────────────

fun savePassword(context: android.content.Context, password: String) {
    context.getSharedPreferences("SafeScreenPrefs", android.content.Context.MODE_PRIVATE)
        .edit().putString("stop_password", password).apply()
}

fun getSavedPassword(context: android.content.Context): String? {
    return context.getSharedPreferences("SafeScreenPrefs", android.content.Context.MODE_PRIVATE)
        .getString("stop_password", null)
}

fun isServiceRunningPref(context: android.content.Context): Boolean {
    return context.getSharedPreferences("SafeScreenPrefs", android.content.Context.MODE_PRIVATE)
        .getBoolean("isServiceRunning", false)
}

// ─────────────────────────────────────────────
//  Home Screen
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit
) {
    val context = LocalContext.current

    var showSetPasswordDialog by remember { mutableStateOf(false) }
    var showStopDialog by remember { mutableStateOf(false) }
    var showAboutSheet by remember { mutableStateOf(false) }
    var isMonitoring by remember { mutableStateOf(isServiceRunningPref(context)) }

    // Pulse animation for the main icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0E1A),
                        Color(0xFF0D1B2A),
                        Color(0xFF0A0E1A)
                    )
                )
            )
    ) {
        // Decorative background circles
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x1A2979FF), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.2f),
                    radius = size.width * 0.5f
                ),
                radius = size.width * 0.5f,
                center = Offset(size.width * 0.15f, size.height * 0.2f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x1400BCD4), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.75f),
                    radius = size.width * 0.45f
                ),
                radius = size.width * 0.45f,
                center = Offset(size.width * 0.85f, size.height * 0.75f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Top bar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 52.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Safe Screen",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (isMonitoring) "Faol himoya" else "Himoya o'chirilgan",
                        fontSize = 13.sp,
                        color = if (isMonitoring) Color(0xFF4CAF50) else Color(0xFF78909C),
                        letterSpacing = 0.3.sp
                    )
                }

                // ── INFO BUTTON: logo from res/mipmap/logo ──
                IconButton(
                    onClick = { showAboutSheet = true },
                    modifier = Modifier
                        .size(84.dp)
                        .background(Color(0xFF1A2540), CircleShape)
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.safe),
                        contentDescription = "Haqida",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Hero icon: logo from res/mipmap/logo ──
            Box(contentAlignment = Alignment.Center) {
                // Outer glow ring
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(pulseScale)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0x552979FF).copy(alpha = glowAlpha * 0.4f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )
                // Inner circle with logo image
                Box(
                    modifier = Modifier
                        .size(148.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF1A2D5A), Color(0xFF112244))
                            ),
                            CircleShape
                        )
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0x662979FF), Color(0x3300BCD4))
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.logo),
                        contentDescription = "Haqida",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = if (isMonitoring) "Monitoring Faol" else "Monitoring O'chirilgan",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Ekraningizni nomaqbul kontentdan\navtomatik himoyalaydi",
                fontSize = 14.sp,
                color = Color(0xFF78909C),
                textAlign = TextAlign.Center,
                lineHeight = 21.sp
            )

            Spacer(Modifier.weight(1f))

            // ── Action cards ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isMonitoring) {
                    PrimaryActionButton(
                        label = "Monitoringni Boshlash",
                        icon = Icons.Default.PlayArrow,
                        gradient = Brush.horizontalGradient(
                            listOf(Color(0xFF1565C0), Color(0xFF0288D1))
                        ),
                        onClick = {
                            onStartMonitoring()
                            isMonitoring = true
                        }
                    )
                } else {
                    PrimaryActionButton(
                        label = "Monitoringni To'xtatish",
                        icon = Icons.Default.Stop,
                        gradient = Brush.horizontalGradient(
                            listOf(Color(0xFFB71C1C), Color(0xFFE53935))
                        ),
                        onClick = { showStopDialog = true }
                    )
                }

                SecondaryActionButton(
                    label = "Parol O'rnatish",
                    description = if (getSavedPassword(context) != null) "Parol o'rnatilgan ✓" else "Hali o'rnatilmagan",
                    icon = Icons.Default.Lock,
                    onClick = { showSetPasswordDialog = true }
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (showSetPasswordDialog) {
        SetPasswordDialog(
            context = context,
            onDismiss = { showSetPasswordDialog = false }
        )
    }

    if (showStopDialog) {
        StopMonitoringDialog(
            context = context,
            onDismiss = { showStopDialog = false },
            onConfirmed = {
                showStopDialog = false
                onStopMonitoring()
                isMonitoring = false
            }
        )
    }

    if (showAboutSheet) {
        AboutBottomSheet(onDismiss = { showAboutSheet = false })
    }
}

// ─────────────────────────────────────────────
//  Reusable Buttons
// ─────────────────────────────────────────────

@Composable
fun PrimaryActionButton(
    label: String,
    icon: ImageVector,
    gradient: Brush,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(gradient)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

@Composable
fun SecondaryActionButton(
    label: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF131C2E))
            .border(1.dp, Color(0xFF1E2D45), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF1A2540), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color(0xFF90CAF9), modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Text(description, fontSize = 12.sp, color = Color(0xFF546E7A))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF37474F), modifier = Modifier.size(20.dp))
        }
    }
}

// ─────────────────────────────────────────────
//  Set Password Dialog
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetPasswordDialog(context: android.content.Context, onDismiss: () -> Unit) {
    val existing = getSavedPassword(context)
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var currentVisible by remember { mutableStateOf(false) }
    var newVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1826),
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF4FC3F7), modifier = Modifier.size(22.dp))
                Text("Parol O'rnatish", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (existing != null) {
                    DialogPasswordField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it; errorMsg = null },
                        label = "Joriy parol",
                        visible = currentVisible,
                        onToggleVisibility = { currentVisible = !currentVisible }
                    )
                }
                DialogPasswordField(
                    value = newPassword,
                    onValueChange = { newPassword = it; errorMsg = null },
                    label = "Yangi parol",
                    visible = newVisible,
                    onToggleVisibility = { newVisible = !newVisible }
                )
                DialogPasswordField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorMsg = null },
                    label = "Parolni tasdiqlang",
                    visible = confirmVisible,
                    onToggleVisibility = { confirmVisible = !confirmVisible }
                )
                AnimatedVisibility(visible = errorMsg != null) {
                    Text(
                        text = errorMsg ?: "",
                        color = Color(0xFFEF5350),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x22EF5350), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF1565C0), Color(0xFF0288D1))))
                    .clickable {
                        when {
                            existing != null && currentPassword != existing ->
                                errorMsg = "Joriy parol noto'g'ri"
                            newPassword.length < 4 ->
                                errorMsg = "Parol kamida 4 ta belgidan iborat bo'lishi kerak"
                            newPassword != confirmPassword ->
                                errorMsg = "Parollar mos kelmadi"
                            else -> {
                                savePassword(context, newPassword)
                                Toast.makeText(context, "Parol muvaffaqiyatli saqlandi ✓", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("Saqlash", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Bekor qilish", color = Color(0xFF546E7A), fontSize = 14.sp)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisibility: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = Color(0xFF546E7A),
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF2979FF),
            unfocusedBorderColor = Color(0xFF1E2D45),
            focusedLabelColor = Color(0xFF4FC3F7),
            unfocusedLabelColor = Color(0xFF546E7A),
            cursorColor = Color(0xFF4FC3F7),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color(0xFFB0BEC5),
            unfocusedContainerColor = Color(0xFF0A1120),
            focusedContainerColor = Color(0xFF0A1120)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

// ─────────────────────────────────────────────
//  Stop Monitoring Dialog
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopMonitoringDialog(
    context: android.content.Context,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    val savedPassword = getSavedPassword(context)
    var enteredPassword by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1826),
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(22.dp))
                Text("Monitoringni To'xtatish", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (savedPassword != null) {
                    Text(
                        "Monitoringni to'xtatish uchun parolni kiriting",
                        color = Color(0xFF78909C),
                        fontSize = 14.sp
                    )
                    DialogPasswordField(
                        value = enteredPassword,
                        onValueChange = { enteredPassword = it; errorMsg = null },
                        label = "Parol",
                        visible = visible,
                        onToggleVisibility = { visible = !visible }
                    )
                    AnimatedVisibility(visible = errorMsg != null) {
                        Text(
                            text = errorMsg ?: "",
                            color = Color(0xFFEF5350),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x22EF5350), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    Text(
                        "Monitoringni to'xtatishni tasdiqlaysizmi?",
                        color = Color(0xFF78909C),
                        fontSize = 14.sp
                    )
                }
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFB71C1C))
                    .clickable {
                        if (savedPassword == null || enteredPassword == savedPassword) {
                            onConfirmed()
                        } else {
                            errorMsg = "Parol noto'g'ri"
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("To'xtatish", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Bekor qilish", color = Color(0xFF546E7A), fontSize = 14.sp)
            }
        }
    )
}

// ─────────────────────────────────────────────
//  About Bottom Sheet
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutBottomSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1826),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 14.dp, bottom = 10.dp)
                    .size(40.dp, 4.dp)
                    .background(Color(0xFF263850), RoundedCornerShape(2.dp))
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App icon row
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0x662979FF), Color(0x3300BCD4))),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {

                Image(
                    painter = painterResource(id = R.mipmap.logo),
                    contentDescription = "Haqida",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("SafeScreen", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("v1.0.0", fontSize = 13.sp, color = Color(0xFF546E7A))
            Spacer(Modifier.height(6.dp))
            Text(
                "Ekraningizni nomaqbul kontentdan\nautomatik himoyalovchi dastur",
                fontSize = 14.sp,
                color = Color(0xFF78909C),
                textAlign = TextAlign.Center,
                lineHeight = 21.sp
            )

            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = Color(0xFF1E2D45))
            Spacer(Modifier.height(20.dp))

            Text(
                "Bog'lanish",
                fontSize = 13.sp,
                color = Color(0xFF546E7A),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            ContactRow(
                icon = Icons.Default.Phone,
                label = "Telefon",
                value = "+998 94 608 99 11",
                color = Color(0xFF4CAF50),
                onClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+998946089911"))
                    context.startActivity(intent)
                }
            )
            ContactRow(
                icon = Icons.Default.Email,
                label = "Email",
                value = "dilmurod21muminov@gmail.com",
                color = Color(0xFF4FC3F7),
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:dilmurod21muminov@gmail.com"))
                    context.startActivity(intent)
                }
            )
            ContactRow(
                icon = Icons.Default.Send,
                label = "Telegram",
                value = "@Safe_Screen",
                color = Color(0xFF29B6F6),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Safe_Screen"))
                    context.startActivity(intent)
                }
            )
            ContactRow(
                icon = Icons.Default.Security,
                label = "Maxfiylik siyosati",
                value = "Privacy Policy",
                color = Color(0xFF9C27B0),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dilmurod21mumin.github.io/safe-screen-policy/"))
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun ContactRow(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0D1520))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 13.sp, color = Color(0xFF546E7A))
                Text(value, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF37474F), modifier = Modifier.size(18.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
}