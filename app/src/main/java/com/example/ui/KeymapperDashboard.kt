package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalDensity
import com.example.data.ButtonType
import com.example.data.GameProfile
import com.example.data.MappedButton
import com.example.service.KeymapperAccessibilityService
import com.example.service.KeymapperEngine
import com.example.service.OverlayWindowManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AppScreen {
    ONBOARDING,
    DASHBOARD,
    EDITOR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeymapperDashboard(
    viewModel: KeymapperViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(AppScreen.ONBOARDING) }
    
    // Periodically poll inputs and permission state
    var isOverlayPermissionGranted by remember { mutableStateOf(false) }
    var isAccessibilityGranted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Query Permission Status periodically
    LaunchedEffect(Unit) {
        while (true) {
            isOverlayPermissionGranted = Settings.canDrawOverlays(context)
            isAccessibilityGranted = KeymapperAccessibilityService.instance != null
            viewModel.checkPeripheralsConnected(context)
            delay(1500)
        }
    }

    // Handle back button presses in editor mode
    if (currentScreen != AppScreen.DASHBOARD && currentScreen != AppScreen.ONBOARDING) {
        BackHandler {
            currentScreen = AppScreen.DASHBOARD
        }
    }

    // Geometric Balance Theme colors and flat dark background
    val themeBackgroundSolid = Color(0xFF1C1B1F)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageOfScreen(currentScreen),
                            contentDescription = "Nexus Icon",
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = when (currentScreen) {
                                AppScreen.ONBOARDING -> "Nexus KeyMapper"
                                AppScreen.DASHBOARD -> "Nexus Cockpit"
                                AppScreen.EDITOR -> "Nexus Canvas"
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif,
                            color = Color(0xFFD0BCFF)
                        )
                    }
                },
                actions = {
                    if (currentScreen != AppScreen.ONBOARDING) {
                        IconButton(onClick = { currentScreen = AppScreen.ONBOARDING }) {
                            Icon(Icons.Default.Info, contentDescription = "Onboarding", tint = Color(0xFF938F99))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1C1B1F),
                    titleContentColor = Color(0xFFD0BCFF)
                )
            )
        },
        containerColor = Color.Transparent,
        modifier = modifier.background(themeBackgroundSolid)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    AppScreen.ONBOARDING -> OnboardingScreen(
                        isOverlayGranted = isOverlayPermissionGranted,
                        isAccessibilityGranted = isAccessibilityGranted,
                        viewModel = viewModel,
                        onProceed = { currentScreen = AppScreen.DASHBOARD }
                    )
                    AppScreen.DASHBOARD -> MainCockpitDashboard(
                        viewModel = viewModel,
                        isOverlayGranted = isOverlayPermissionGranted,
                        isAccessibilityGranted = isAccessibilityGranted,
                        onEnterEditor = { profile ->
                            viewModel.selectProfileForEditing(profile)
                            currentScreen = AppScreen.EDITOR
                        }
                    )
                    AppScreen.EDITOR -> MappedLayoutEditor(
                        viewModel = viewModel,
                        onBack = { currentScreen = AppScreen.DASHBOARD }
                    )
                }
            }
        }
    }
}

private fun imageOfScreen(screen: AppScreen) = when (screen) {
    AppScreen.ONBOARDING -> Icons.Default.Info
    AppScreen.DASHBOARD -> Icons.Default.Gamepad
    AppScreen.EDITOR -> Icons.Default.Settings
}

@Composable
fun OnboardingScreen(
    isOverlayGranted: Boolean,
    isAccessibilityGranted: Boolean,
    viewModel: KeymapperViewModel,
    onProceed: () -> Unit
) {
    val context = LocalContext.current
    val kbConnected by viewModel.keyboardConnected.collectAsState()
    val msConnected by viewModel.mouseConnected.collectAsState()
    val isChromebookMode by viewModel.isChromebookMode.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero visual branding header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF2B2930))
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(28.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "NEXUS KEYMAPPER",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF),
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "Professional FPS Input Interceptor Daemon",
                        fontSize = 12.sp,
                        color = Color(0xFF938F99),
                        modifier = Modifier.padding(top = 4.dp),
                        fontFamily = FontFamily.SansSerif
                    )

                    if (isChromebookMode) {
                        Row(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF381E72))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Laptop,
                                contentDescription = "Chromebook",
                                tint = Color(0xFFD0BCFF),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "CHROMEBOOK HARDWARE ACTIVE 💻",
                                color = Color(0xFFD0BCFF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PeripheralBadge(label = "KEYBOARD", isConnected = kbConnected, icon = Icons.Default.Keyboard)
                        PeripheralBadge(label = "MOUSE", isConnected = msConnected, icon = Icons.Default.Mouse)
                    }
                }
            }
        }

        // Section header
        item {
            Text(
                "REQUIRED PERMISSIONS SYSTEM SETUP",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF938F99),
                modifier = Modifier.fillMaxWidth(),
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 0.5.sp
            )
        }

        // Display over other apps
        item {
            PermissionSetupCard(
                title = "1. Display over other apps (Overlay)",
                description = "Required to render customizable controller panels, trigger labels, and transparent aiming overlays floating on top of active 3D games.",
                isGranted = isOverlayGranted,
                onGrantClick = {
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        context.startActivity(intent)
                    }
                }
            )
        }

        // Accessibility Service setup
        item {
            PermissionSetupCard(
                title = "2. Nexus Keymapper Accessibility",
                description = "Required to inject high-precision touch taps, drags, and swipes representing physical peripherals on screen without requiring root access.",
                isGranted = isAccessibilityGranted,
                onGrantClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            )
        }

        // Play guide tips card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF49454F)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "QUICK START HOW-TO GUIDE",
                        color = Color(0xFFD0BCFF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    GuideTipItem(num = "1", text = "Ensure permissions are granted (should turn glowing Lavender above).")
                    if (isChromebookMode) {
                        GuideTipItem(num = "2", text = "Your Chromebook's integrated physical keyboard and trackpad/mouse are auto-linked!")
                    } else {
                        GuideTipItem(num = "2", text = "Connect keyboard and mouse using an OTG USB Hub or Bluetooth.")
                    }
                    GuideTipItem(num = "3", text = "Choose/Create game mapping layout, and press 'ACTIVATE LAYOUT OVERLAYS'.")
                    if (isChromebookMode) {
                        GuideTipItem(num = "4", text = "Inside game, press [TAB] on your Chromebook keyboard to lock/capture mouse focus, and press [TAB] again to release.")
                    } else {
                        GuideTipItem(num = "4", text = "Start the target shooter. Tap [TAB] on keyboard to lock the cursor into high-responsiveness relative FPS aiming, and tap [TAB] again to unlock for standard inventory cursor management.")
                    }
                }
            }
        }

        // Master Launch Button
        item {
            Button(
                onClick = onProceed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("onboarding_proceed_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD0BCFF),
                    contentColor = Color(0xFF381E72)
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ENTER CONTROL COCKPIT   ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Icon(Icons.Default.PlayArrow, contentDescription = "Enter")
                }
            }
        }
    }
}

@Composable
fun PeripheralBadge(label: String, isConnected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2B2930))
            .border(
                1.dp,
                if (isConnected) Color(0xFFD0BCFF) else Color(0xFF49454F),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isConnected) Color(0xFFD0BCFF) else Color(0xFF938F99),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "$label: ${if (isConnected) "CONNECTED" else "MISSING"}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                color = if (isConnected) Color(0xFFD0BCFF) else Color(0xFF938F99)
            )
        }
    }
}

@Composable
fun PermissionSetupCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF49454F)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6E1E9),
                    fontFamily = FontFamily.SansSerif
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isGranted) Color(0xFF381E72) else Color(0x33EF4444))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (isGranted) "GRANTED" else "NOT ENABLED",
                        color = if (isGranted) Color(0xFFD0BCFF) else Color(0xFFEF4444),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
            Text(
                description,
                fontSize = 12.sp,
                color = Color(0xFF938F99),
                modifier = Modifier.padding(vertical = 12.dp)
            )
            if (!isGranted) {
                Button(
                    onClick = onGrantClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD0BCFF),
                        contentColor = Color(0xFF381E72)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("AUTHORIZE SYSTEM PERMISSION", fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = Color(0xFFD0BCFF))
                    Text("   Ready & Fully Managed.", color = Color(0xFFE6E1E9), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                }
            }
        }
    }
}

@Composable
fun GuideTipItem(num: String, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFF381E72)),
            contentAlignment = Alignment.Center
        ) {
            Text(num, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = Color(0xFFE6E1E9), fontFamily = FontFamily.SansSerif)
    }
}

@Composable
fun MainCockpitDashboard(
    viewModel: KeymapperViewModel,
    isOverlayGranted: Boolean,
    isAccessibilityGranted: Boolean,
    onEnterEditor: (GameProfile) -> Unit
) {
    val context = LocalContext.current
    val profiles by viewModel.profiles.collectAsState()
    val logs by KeymapperEngine.engineLog.collectAsState()
    val isChromebookMode by viewModel.isChromebookMode.collectAsState()

    var activeProfileLocal = KeymapperEngine.activeProfile
    var isOverlayActiveLocal = KeymapperEngine.isOverlayShowing
    var isMouseLockedLocal = KeymapperEngine.isMouseLocked

    var showCreateDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var newPackageId by remember { mutableStateOf("") }

    // Advanced Save/Load/Cloning states
    var showCloneDialog by remember { mutableStateOf(false) }
    var cloneSourceProfileId by remember { mutableStateOf(-1) }
    var cloneNewName by remember { mutableStateOf("") }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameProfileId by remember { mutableStateOf(-1) }
    var renameNewName by remember { mutableStateOf("") }
    var renameNewPackageId by remember { mutableStateOf("") }

    var showExportDialog by remember { mutableStateOf(false) }
    var exportProfileName by remember { mutableStateOf("") }
    var exportJsonContent by remember { mutableStateOf("") }

    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonContent by remember { mutableStateOf("") }

    var dummyRecomposeTrigger by remember { mutableStateOf(0) }
    var activeDropdownPackage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dummyRecomposeTrigger) {
        // Keeps local visual hooks in sync
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Chromebook Hardware Panel
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.3f), RoundedCornerShape(28.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Laptop,
                                contentDescription = "Chromebook",
                                tint = Color(0xFFD0BCFF),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "CHROMEBOOK INTEGRATION STATION",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD0BCFF),
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = 0.5.sp
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF381E72))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "CHROMEOS LINKED",
                                color = Color(0xFFD0BCFF),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                    
                    Text(
                        "Chromebook architecture detected. Built-in physical keyboard and touch pad/mouse are fully integrated and pre-synchronized with the interceptor daemon.",
                        fontSize = 12.sp,
                        color = Color(0xFF938F99),
                        modifier = Modifier.padding(vertical = 12.dp),
                        fontFamily = FontFamily.SansSerif
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1C1B1F),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF49454F))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Keyboard, contentDescription = "Keyboard", tint = Color(0xFFD0BCFF), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("KB: ACTIVE 💻", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF), fontFamily = FontFamily.SansSerif)
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1C1B1F),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF49454F))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Mouse, contentDescription = "Mouse", tint = Color(0xFFD0BCFF), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("MOUSE: ACTIVE 🖱️", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF), fontFamily = FontFamily.SansSerif)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = Color(0xFF49454F))
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Chromebook Mode Optimization", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E9), fontFamily = FontFamily.SansSerif)
                            Text("Keep hardware inputs actively pre-linked even when default Android polling remains silent.", fontSize = 10.sp, color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                        }
                        Switch(
                            checked = isChromebookMode,
                            onCheckedChange = { viewModel.setChromebookMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFD0BCFF),
                                checkedTrackColor = Color(0xFF381E72),
                                uncheckedThumbColor = Color(0xFF938F99),
                                uncheckedTrackColor = Color(0xFF1C1B1F)
                            )
                        )
                    }
                }
            }
        }

        // Active Controller state dials
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(28.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "ENGINE CONTROLLER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF),
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Active Profile Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("ACTIVE ENGINE PROFILE", fontSize = 11.sp, color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                            Text(
                                activeProfileLocal?.name ?: "NO ACTIVE PROFILE LOADED",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeProfileLocal != null) Color(0xFFE6E1E9) else Color(0xFFFF4D4D)
                            )
                        }

                        if (activeProfileLocal == null && profiles.isNotEmpty()) {
                            Button(
                                onClick = {
                                    viewModel.makeProfileActive(profiles.first(), context)
                                    dummyRecomposeTrigger++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("LOAD DEFAULT", fontSize = 10.sp, color = Color(0xFFD0BCFF), fontFamily = FontFamily.SansSerif)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color(0xFF49454F))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Floating overlays toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("FLOATING LAYOUT OVERLAYS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E9), fontFamily = FontFamily.SansSerif)
                            Text("Draws joystick overlay bubble 🎮 and visual feedback key labels inside other apps.", fontSize = 11.sp, color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                        }
                        Switch(
                            checked = isOverlayActiveLocal,
                            onCheckedChange = { active ->
                                if (!isOverlayGranted) {
                                    Toast.makeText(context, "Grant 'Overlay' permission first!", Toast.LENGTH_SHORT).show()
                                    return@Switch
                                }
                                if (active) {
                                    if (KeymapperEngine.activeProfile == null) {
                                        Toast.makeText(context, "Select/Activate a game profile first!", Toast.LENGTH_SHORT).show()
                                        return@Switch
                                    }
                                    OverlayWindowManager.showOverlays(context)
                                } else {
                                    OverlayWindowManager.hideOverlays(context)
                                }
                                dummyRecomposeTrigger++
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFD0BCFF),
                                checkedTrackColor = Color(0xFF381E72),
                                uncheckedThumbColor = Color(0xFF938F99),
                                uncheckedTrackColor = Color(0xFF1C1B1F)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Aim sensitive slider
                    activeProfileLocal?.let { profile ->
                        var sensSlider by remember(profile.id) { mutableStateOf(profile.sensitivity) }
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("GLOBAL MOUSE SENSITIVITY", fontSize = 11.sp, color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                                Text("${"%.1f".format(sensSlider)}x", fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF), fontFamily = FontFamily.SansSerif)
                            }
                            Slider(
                                value = sensSlider,
                                onValueChange = {
                                    sensSlider = it
                                    viewModel.updateSensitivity(profile, it, context)
                                },
                                valueRange = 0.2f..4.0f,
                                steps = 38,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFD0BCFF),
                                    activeTrackColor = Color(0xFFD0BCFF),
                                    inactiveTrackColor = Color(0xFF49454F)
                                )
                            )
                        }
                    }
                }
            }
        }

        // ADVANCED MOUSE LOCK & SENSITIVITY CONTROL DECK
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(28.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "💻 Chromebook Mouse Lock & Look Core",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF),
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Lock Status & Manual Trigger action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1D1B20))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("CURSOR POINTER STATUS", fontSize = 9.sp, color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                            Text(
                                if (KeymapperEngine.isMouseLocked) "LOCKED 🔒 AIM MODEL" else "UNLOCKED 🔓 CURSOR",
                                color = if (KeymapperEngine.isMouseLocked) Color(0xFF81C784) else Color(0xFFFFB74D),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }

                        Button(
                            onClick = {
                                if (!isOverlayGranted) {
                                    Toast.makeText(context, "Grant 'Overlay' permission first!", Toast.LENGTH_SHORT).show()
                                } else if (KeymapperEngine.activeProfile == null) {
                                    Toast.makeText(context, "Please select/activate a game profile first!", Toast.LENGTH_SHORT).show()
                                } else {
                                    // Make sure overlays are on
                                    if (!KeymapperEngine.isOverlayShowing) {
                                        OverlayWindowManager.showOverlays(context)
                                    }
                                    KeymapperEngine.toggleMouseLock()
                                    OverlayWindowManager.updateLockState(context)
                                    dummyRecomposeTrigger++
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (KeymapperEngine.isMouseLocked) Color(0xFFBA1A1A) else Color(0xFF381E72)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                if (KeymapperEngine.isMouseLocked) "UNLOCK (${KeymapperEngine.mouseLockKeyName})" else "LOCK MOUSE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // CUSTOMIZABLE MOUSE LOCK SHORTCUT SELECTOR
                    Text("LOCK / UNLOCK SHORTCUT HOTKEY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Options: TAB, ESC, BACKQUOTE, F1
                        listOf("TAB" to 61, "ESC" to 111, "~ ACCENT" to 68, "F1" to 131).forEach { (name, code) ->
                            val isSelected = KeymapperEngine.mouseLockKeyCode == code
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF381E72) else Color(0xFF1D1B20))
                                    .border(1.dp, if (isSelected) Color(0xFFD0BCFF) else Color(0xFF49454F), RoundedCornerShape(8.dp))
                                    .clickable {
                                        KeymapperEngine.mouseLockKeyCode = code
                                        KeymapperEngine.mouseLockKeyName = name
                                        Toast.makeText(context, "Lock toggler bound to $name", Toast.LENGTH_SHORT).show()
                                        dummyRecomposeTrigger++
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color(0xFFE6E1E9) else Color(0xFF938F99),
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color(0xFF49454F))
                    Spacer(modifier = Modifier.height(16.dp))

                    // AUTOMATIC FIRE TAP CONFIGURATION
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Fire Tap (Hold Left Mouse)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E9), fontFamily = FontFamily.SansSerif)
                            Text("When holding Left-Click, programmatically spam rapid touches on the Fire (LClick) overlay target.", fontSize = 11.sp, color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                        }
                        Switch(
                            checked = KeymapperEngine.isAutoFireEnabled,
                            onCheckedChange = {
                                KeymapperEngine.isAutoFireEnabled = it
                                dummyRecomposeTrigger++
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFD0BCFF),
                                checkedTrackColor = Color(0xFF381E72),
                                uncheckedThumbColor = Color(0xFF938F99),
                                uncheckedTrackColor = Color(0xFF1C1B1F)
                            )
                        )
                    }

                    if (KeymapperEngine.isAutoFireEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("AUTO-FIRE REPEAT SPEED", fontSize = 11.sp, color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                                Text("${KeymapperEngine.autoFireRateMs} ms", fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF), fontFamily = FontFamily.SansSerif)
                            }
                            Slider(
                                value = KeymapperEngine.autoFireRateMs.toFloat(),
                                onValueChange = {
                                    KeymapperEngine.autoFireRateMs = it.toLong()
                                },
                                valueRange = 50f..300f,
                                steps = 25,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFD0BCFF),
                                    activeTrackColor = Color(0xFFD0BCFF)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color(0xFF49454F))
                    Spacer(modifier = Modifier.height(16.dp))

                    // ADVANCED INDEPENDENT LOOK SENSITIVITIES
                    Text("INDEPENDENT LOOK SENSITIVITY CALIBRATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                    Spacer(modifier = Modifier.height(12.dp))

                    // X AXIS LOOK
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("HORIZONTAL SENSITIVITY (X-AXIS)", fontSize = 11.sp, color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                            Text("${"%.2f".format(KeymapperEngine.mouseSensitivityX)}x", fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF), fontFamily = FontFamily.SansSerif)
                        }
                        Slider(
                            value = KeymapperEngine.mouseSensitivityX,
                            onValueChange = {
                                KeymapperEngine.mouseSensitivityX = it
                            },
                            valueRange = 0.2f..4.0f,
                            steps = 38,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFD0BCFF),
                                activeTrackColor = Color(0xFFD0BCFF)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Y AXIS LOOK
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("VERTICAL SENSITIVITY (Y-AXIS)", fontSize = 11.sp, color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                            Text("${"%.2f".format(KeymapperEngine.mouseSensitivityY)}x", fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF), fontFamily = FontFamily.SansSerif)
                        }
                        Slider(
                            value = KeymapperEngine.mouseSensitivityY,
                            onValueChange = {
                                KeymapperEngine.mouseSensitivityY = it
                            },
                            valueRange = 0.2f..4.0f,
                            steps = 38,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFD0BCFF),
                                activeTrackColor = Color(0xFFD0BCFF)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color(0xFF49454F))
                    Spacer(modifier = Modifier.height(16.dp))

                    // MOUSE INPUT SMOOTHING
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("MOUSE INPUT SMOOTHING", fontSize = 11.sp, color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                            Text(
                                if (KeymapperEngine.mouseSmoothing == 0f) "OFF (RAW)" else "${(KeymapperEngine.mouseSmoothing * 100).toInt()}%", 
                                fontWeight = FontWeight.Bold, 
                                color = Color(0xFFD0BCFF), 
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                        Text(
                            text = "Reduces micro-jitter when converting continuous mouse glide into emulation sweeps.",
                            fontSize = 10.sp,
                            color = Color(0xFF938F99).copy(alpha = 0.7f),
                            fontFamily = FontFamily.SansSerif,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Slider(
                            value = KeymapperEngine.mouseSmoothing,
                            onValueChange = {
                                KeymapperEngine.mouseSmoothing = it
                            },
                            valueRange = 0f..0.9f,
                            steps = 18,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFD0BCFF),
                                activeTrackColor = Color(0xFFD0BCFF),
                                inactiveTrackColor = Color(0xFF49454F)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color(0xFF49454F))
                    Spacer(modifier = Modifier.height(16.dp))

                    // OVERLAY MARKERS TRANSPARENCY
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("OVERLAY TRANSPARENCY", fontSize = 11.sp, color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                            Text(
                                text = "${(KeymapperEngine.overlayOpacity * 100).toInt()}%", 
                                fontWeight = FontWeight.Bold, 
                                color = Color(0xFFD0BCFF), 
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                        Text(
                            text = "Adjust the transparency of visual key-mapping badges to balance clarity with game immersion.",
                            fontSize = 10.sp,
                            color = Color(0xFF938F99).copy(alpha = 0.7f),
                            fontFamily = FontFamily.SansSerif,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Slider(
                            value = KeymapperEngine.overlayOpacity,
                            onValueChange = {
                                KeymapperEngine.overlayOpacity = it
                                OverlayWindowManager.updateOverlayOpacity()
                            },
                            valueRange = 0.05f..1.0f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFD0BCFF),
                                activeTrackColor = Color(0xFFD0BCFF),
                                inactiveTrackColor = Color(0xFF49454F)
                            )
                        )
                    }
                }
            }
        }

        // DYNAMIC FPS GAME LAUNCHER & PROFILE LINKER WINDOW
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1A22)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "🎮 FPS GAME LAUNCH HUB",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD0BCFF),
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                "Auto-detects installed games and couples profiles instantly",
                                fontSize = 10.sp,
                                color = Color(0xFF938F99),
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFD0BCFF).copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "APP DETECTOR",
                                color = Color(0xFFD0BCFF),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val gamesToDetect = remember {
                        listOf(
                            mapOf(
                                "id" to "free_fire",
                                "name" to "Free Fire",
                                "packageId" to "com.dts.freefireth",
                                "desc" to "Garena Battle Royale",
                                "color" to "0xFFFF5722",
                                "abbr" to "FF"
                            ),
                            mapOf(
                                "id" to "pubg",
                                "name" to "PUBG Mobile",
                                "packageId" to "com.tencent.ig",
                                "desc" to "Tactical Battlegrounds",
                                "color" to "0xFFFFC107",
                                "abbr" to "PUBG"
                            ),
                            mapOf(
                                "id" to "cod",
                                "name" to "COD Mobile",
                                "packageId" to "com.activision.callofduty.shooter",
                                "desc" to "Multiplayer Ops",
                                "color" to "0xFF8D6E63",
                                "abbr" to "CODM"
                            ),
                            mapOf(
                                "id" to "standoff2",
                                "name" to "Standoff 2",
                                "packageId" to "com.axlebolt.standoff2",
                                "desc" to "Tactical Strike Arena",
                                "color" to "0xFF03A9F4",
                                "abbr" to "SO2"
                            ),
                            mapOf(
                                "id" to "critical_ops",
                                "name" to "Critical Ops",
                                "packageId" to "com.criticalforceentertainment.criticalops",
                                "desc" to "Competitive 5v5 FPS Strike",
                                "color" to "0xFF9C27B0",
                                "abbr" to "COPS"
                            ),
                            mapOf(
                                "id" to "hill_climb",
                                "name" to "Hill Climb 1",
                                "packageId" to "com.fingersoft.hillclimb",
                                "desc" to "Fingersoft Calibration Demo",
                                "color" to "0xFF4CAF50",
                                "abbr" to "HCR"
                            )
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        gamesToDetect.forEach { gameData ->
                            val packId = gameData["packageId"]!!
                            val gameName = gameData["name"]!!
                            val gameAbbr = gameData["abbr"]!!
                            val gameDesc = gameData["desc"]!!
                            val tintHex = gameData["color"]!!
                            val accentColor = Color(android.graphics.Color.parseColor(tintHex))

                            // Check installation status
                            val isInstalled = remember(packId, dummyRecomposeTrigger) {
                                try {
                                    context.packageManager.getPackageInfo(packId, 0)
                                    true
                                } catch (e: Exception) {
                                    false
                                }
                            }

                            val linkedProfile = profiles.find { it.packageId == packId }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF232125))
                                    .border(
                                        width = 1.dp,
                                        color = if (isInstalled) accentColor.copy(alpha = 0.4f) else Color(0xFF49454F).copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Game Icon Emblem
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(accentColor.copy(alpha = 0.15f))
                                        .border(1.dp, accentColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = gameAbbr,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }

                                // Game Info labels
                                Column(modifier = Modifier.weight(1.1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = gameName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (isInstalled) Color(0xFF4CAF50).copy(alpha = 0.15f)
                                                    else Color(0xFF938F99).copy(alpha = 0.12f)
                                                )
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = if (isInstalled) "● INSTALLED" else "● NOT FOUND",
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isInstalled) Color(0xFF81C784) else Color(0xFFB0BEC5),
                                                fontFamily = FontFamily.SansSerif
                                            )
                                        }
                                    }
                                    Text(
                                        text = gameDesc,
                                        fontSize = 9.sp,
                                        color = Color(0xFF938F99),
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }

                                // Interactive Profile Selector Dropdown
                                Box(modifier = Modifier.wrapContentSize()) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF141218))
                                            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(8.dp))
                                            .clickable { activeDropdownPackage = packId }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = linkedProfile?.name ?: "No Profile",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (linkedProfile != null) Color(0xFFD0BCFF) else Color(0xFF938F99),
                                            fontFamily = FontFamily.SansSerif,
                                            maxLines = 1
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Select Profile",
                                            tint = Color(0xFFD0BCFF),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = activeDropdownPackage == packId,
                                        onDismissRequest = { activeDropdownPackage = null },
                                        modifier = Modifier
                                            .background(Color(0xFF2B2930))
                                            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(8.dp))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("None (Unlink)", color = Color(0xFFEF9A9A), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                            onClick = {
                                                if (linkedProfile != null) {
                                                    viewModel.linkProfileToPackage(linkedProfile.id, "")
                                                }
                                                activeDropdownPackage = null
                                                dummyRecomposeTrigger++
                                                Toast.makeText(context, "Unlinked keymap profile from $gameName", Toast.LENGTH_SHORT).show()
                                            }
                                        )

                                        profiles.forEach { p ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(p.name, color = Color(0xFFE6E1E9), fontSize = 11.sp)
                                                        if (p.packageId == packId) {
                                                            Icon(Icons.Default.Check, contentDescription = "Active Link", tint = Color(0xFFD0BCFF), modifier = Modifier.size(12.dp))
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    viewModel.linkProfileToPackage(p.id, packId)
                                                    activeDropdownPackage = null
                                                    dummyRecomposeTrigger++
                                                    Toast.makeText(context, "Associated profile '${p.name}' with $gameName!", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }

                                        Divider(color = Color(0xFF49454F))

                                        DropdownMenuItem(
                                            text = { Text("+ Create layout template", color = Color(0xFFD0BCFF), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                            onClick = {
                                                viewModel.createNewProfile("$gameName Layout", packId, context)
                                                activeDropdownPackage = null
                                                dummyRecomposeTrigger++
                                                Toast.makeText(context, "Generating custom layout for $gameName...", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }

                                // Quick Action Launch/Simulation Button
                                Button(
                                    onClick = {
                                        val targetProfile = linkedProfile ?: profiles.firstOrNull()
                                        if (targetProfile == null) {
                                            Toast.makeText(context, "No profile exists! Create a custom layout profile first.", Toast.LENGTH_LONG).show()
                                            return@Button
                                        }

                                        // 1. Set keymap profile as Active
                                        viewModel.makeProfileActive(targetProfile, context)

                                        // 2. Resolve package launch intent
                                        val launchIntent = context.packageManager.getLaunchIntentForPackage(packId)
                                        if (launchIntent != null) {
                                            Toast.makeText(
                                                context, 
                                                "Active profile loaded: '${targetProfile.name}'. Opening $gameName...", 
                                                Toast.LENGTH_LONG
                                            ).show()
                                            context.startActivity(launchIntent)
                                        } else {
                                            Toast.makeText(
                                                context, 
                                                "Loaded: '${targetProfile.name}'. Simulating launch of $gameName (Not Installed). Starting key mapper layout overlays...", 
                                                Toast.LENGTH_LONG
                                            ).show()
                                            if (!KeymapperEngine.isOverlayShowing) {
                                                OverlayWindowManager.showOverlays(context)
                                            }
                                            dummyRecomposeTrigger++
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isInstalled) accentColor else Color(0xFF313033),
                                        contentColor = if (isInstalled) Color.White else Color(0xFFD0BCFF)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = if (isInstalled) "LAUNCH 🚀" else "TEST 🎮",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "GAME CONTROL PROFILES",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF938F99),
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.5.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Import JSON Button
                    OutlinedButton(
                        onClick = {
                            importJsonContent = ""
                            showImportDialog = true
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD0BCFF)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Download, 
                            contentDescription = "Import JSON", 
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(" IMPORT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF), fontFamily = FontFamily.SansSerif)
                    }

                    // Create New Profile Button
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF381E72),
                            contentColor = Color(0xFFD0BCFF)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Add, 
                            contentDescription = "New", 
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(" NEW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF), fontFamily = FontFamily.SansSerif)
                    }
                }
            }
        }

        // Profile Cards list
        if (profiles.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Generating Profile Presets inside Database...", color = Color(0xFF938F99), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                }
            }
        } else {
            items(profiles) { profile ->
                val isActive = activeProfileLocal?.id == profile.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            2.dp,
                            if (isActive) Color(0xFFD0BCFF) else Color.Transparent,
                            RoundedCornerShape(28.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        profile.name,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE6E1E9),
                                        fontFamily = FontFamily.SansSerif
                                    )
                                    if (isActive) {
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 8.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF381E72))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "ACTIVE",
                                                color = Color(0xFFD0BCFF),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.SansSerif
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (profile.packageId.isNotEmpty()) profile.packageId else "All Games Default Layout",
                                    fontSize = 11.sp,
                                    color = Color(0xFF938F99),
                                    fontFamily = FontFamily.SansSerif
                                )
                            }

                            // Active Profile switch button
                            Button(
                                onClick = {
                                    viewModel.makeProfileActive(profile, context)
                                    dummyRecomposeTrigger++
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isActive) Color(0x33D0BCFF) else Color(0xFF381E72),
                                    contentColor = Color(0xFFD0BCFF)
                                ),
                                border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD0BCFF)) else null,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    if (isActive) "RELOAD MAP" else "ACTIVATE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD0BCFF),
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Edit key bindings mapping button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1C1B1F))
                                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(12.dp))
                                    .clickable { onEnterEditor(profile) },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFFD0BCFF), modifier = Modifier.size(14.dp))
                                    Text("  MAP KEYS", color = Color(0xFFD0BCFF), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                                }
                            }

                            // Clone / Duplicate Profile
                            IconButton(
                                onClick = {
                                    cloneSourceProfileId = profile.id
                                    cloneNewName = "${profile.name} (Copy)"
                                    showCloneDialog = true
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1C1B1F))
                                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Profile", tint = Color(0xFFD0BCFF), modifier = Modifier.size(16.dp))
                            }

                            // Rename / Edit Details Profile
                            IconButton(
                                onClick = {
                                    renameProfileId = profile.id
                                    renameNewName = profile.name
                                    renameNewPackageId = profile.packageId
                                    showRenameDialog = true
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1C1B1F))
                                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.Build, contentDescription = "Edit Details", tint = Color(0xFFD0BCFF), modifier = Modifier.size(16.dp))
                            }

                            // Export JSON sharing layout
                            IconButton(
                                onClick = {
                                    viewModel.exportProfileToJson(profile.id) { json ->
                                        exportProfileName = profile.name
                                        exportJsonContent = json
                                        showExportDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1C1B1F))
                                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Export Profile", tint = Color(0xFFD0BCFF), modifier = Modifier.size(16.dp))
                            }

                            // Delete Profile Trigger
                            IconButton(
                                onClick = { viewModel.deleteProfile(profile); dummyRecomposeTrigger++ },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF330B14))
                                    .border(1.dp, Color(0xFF490B14), RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Live Diagnostic peripheral intercept logs console
        item {
            Card(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF000000)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF49454F).copy(alpha = 0.6f), RoundedCornerShape(32.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "LIVE INTERCEPT LOGS CONSOLE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF),
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    ) {
                        if (logs.isEmpty()) {
                            Text(
                                "Awakening input listeners... Click keys on controller to monitor intercept.",
                                color = Color(0xFF938F99),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            LazyColumn(reverseLayout = false) {
                                items(logs) { logMsg ->
                                    Text(
                                        logMsg,
                                        color = if (logMsg.contains("Key Intercepted")) Color(0xFFD0BCFF) else Color(0xFFE6E1E9),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal adding Custom profiles
    if (showCreateDialog) {
        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(28.dp))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "CREATE CUSTOM GAME LAYOUT",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6E1E9),
                        fontFamily = FontFamily.SansSerif
                    )

                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("Profile Name (e.g. Free Fire)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE6E1E9),
                            unfocusedTextColor = Color(0xFF938F99),
                            focusedBorderColor = Color(0xFFD0BCFF),
                            unfocusedBorderColor = Color(0xFF49454F),
                            focusedLabelColor = Color(0xFFD0BCFF),
                            unfocusedLabelColor = Color(0xFF938F99)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_profile_name_input")
                    )

                    OutlinedTextField(
                        value = newPackageId,
                        onValueChange = { newPackageId = it },
                        label = { Text("Game Package (Optional)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE6E1E9),
                            unfocusedTextColor = Color(0xFF938F99),
                            focusedBorderColor = Color(0xFFD0BCFF),
                            unfocusedBorderColor = Color(0xFF49454F),
                            focusedLabelColor = Color(0xFFD0BCFF),
                            unfocusedLabelColor = Color(0xFF938F99)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_profile_package_input")
                    )

                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showCreateDialog = false }) {
                            Text("CANCEL", color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (newProfileName.isNotBlank()) {
                                    viewModel.createNewProfile(newProfileName, newPackageId, context)
                                    newProfileName = ""
                                    newPackageId = ""
                                    showCreateDialog = false
                                    dummyRecomposeTrigger++
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF381E72),
                                contentColor = Color(0xFFD0BCFF)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("add_profile_save_btn")
                        ) {
                            Text("CREATE", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        }
                    }
                }
            }
        }
    }

    // Duplicate Profile Card Modal Dialog
    if (showCloneDialog) {
        Dialog(onDismissRequest = { showCloneDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(28.dp))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "DUPLICATE KEYMAP PROFILE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF),
                        fontFamily = FontFamily.SansSerif
                    )

                    Text(
                        "Make an independent copy of this layout template to customize its bounds safely.",
                        fontSize = 12.sp,
                        color = Color(0xFF938F99),
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.SansSerif
                    )

                    OutlinedTextField(
                        value = cloneNewName,
                        onValueChange = { cloneNewName = it },
                        label = { Text("New Profile Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE6E1E9),
                            unfocusedTextColor = Color(0xFF938F99),
                            focusedBorderColor = Color(0xFFD0BCFF),
                            unfocusedBorderColor = Color(0xFF49454F),
                            focusedLabelColor = Color(0xFFD0BCFF),
                            unfocusedLabelColor = Color(0xFF938F99)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showCloneDialog = false }) {
                            Text("CANCEL", color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (cloneNewName.isNotBlank() && cloneSourceProfileId != -1) {
                                    viewModel.cloneProfile(cloneSourceProfileId, cloneNewName)
                                    showCloneDialog = false
                                    cloneNewName = ""
                                    cloneSourceProfileId = -1
                                    dummyRecomposeTrigger++
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF381E72),
                                contentColor = Color(0xFFD0BCFF)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("DUPLICATE", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        }
                    }
                }
            }
        }
    }

    // Rename Profile Modal Dialog
    if (showRenameDialog) {
        Dialog(onDismissRequest = { showRenameDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(28.dp))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "EDIT PROFILE METADATA",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF),
                        fontFamily = FontFamily.SansSerif
                    )

                    OutlinedTextField(
                        value = renameNewName,
                        onValueChange = { renameNewName = it },
                        label = { Text("Profile Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE6E1E9),
                            unfocusedTextColor = Color(0xFF938F99),
                            focusedBorderColor = Color(0xFFD0BCFF),
                            unfocusedBorderColor = Color(0xFF49454F),
                            focusedLabelColor = Color(0xFFD0BCFF),
                            unfocusedLabelColor = Color(0xFF938F99)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = renameNewPackageId,
                        onValueChange = { renameNewPackageId = it },
                        label = { Text("Game Package Id (Optional)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE6E1E9),
                            unfocusedTextColor = Color(0xFF938F99),
                            focusedBorderColor = Color(0xFFD0BCFF),
                            unfocusedBorderColor = Color(0xFF49454F),
                            focusedLabelColor = Color(0xFFD0BCFF),
                            unfocusedLabelColor = Color(0xFF938F99)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showRenameDialog = false }) {
                            Text("CANCEL", color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (renameNewName.isNotBlank() && renameProfileId != -1) {
                                    viewModel.renameProfile(renameProfileId, renameNewName, renameNewPackageId)
                                    showRenameDialog = false
                                    renameProfileId = -1
                                    dummyRecomposeTrigger++
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF381E72),
                                contentColor = Color(0xFFD0BCFF)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("UPDATE", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        }
                    }
                }
            }
        }
    }

    // Export Sharing Text Modal Dialog
    if (showExportDialog) {
        Dialog(onDismissRequest = { showExportDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(28.dp))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "SHARE PROFILE MAPPING CODE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF),
                        fontFamily = FontFamily.SansSerif
                    )

                    Text(
                        "Copy this configuration code block to share your mapping configuration with others:",
                        fontSize = 11.sp,
                        color = Color(0xFF938F99),
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.SansSerif
                    )

                    OutlinedTextField(
                        value = exportJsonContent,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Mapping JSON Code") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF81C784),
                            unfocusedTextColor = Color(0xFF81C784),
                            focusedBorderColor = Color(0xFF49454F),
                            unfocusedBorderColor = Color(0xFF49454F)
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Keymapper Profile - $exportProfileName", exportJsonContent)
                                    clipboardManager.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied profile JSON to clipboard!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to copy to clipboard", Toast.LENGTH_SHORT).show()
                                }
                                showExportDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF381E72),
                                contentColor = Color(0xFFD0BCFF)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("📋 COPY CODE", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(onClick = { showExportDialog = false }) {
                            Text("DONE", color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                        }
                    }
                }
            }
        }
    }

    // Import Sharing Text Modal Dialog
    if (showImportDialog) {
        Dialog(onDismissRequest = { showImportDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(28.dp))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "IMPORT PROFILE CODE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF),
                        fontFamily = FontFamily.SansSerif
                    )

                    Text(
                        "Paste a backup JSON configuration code to load it instantly as a new active game layout profile:",
                        fontSize = 11.sp,
                        color = Color(0xFF938F99),
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.SansSerif
                    )

                    OutlinedTextField(
                        value = importJsonContent,
                        onValueChange = { importJsonContent = it },
                        placeholder = { Text("Paste JSON code here...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE6E1E9),
                            unfocusedTextColor = Color(0xFF938F99),
                            focusedBorderColor = Color(0xFFD0BCFF),
                            unfocusedBorderColor = Color(0xFF49454F)
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showImportDialog = false }) {
                            Text("CANCEL", color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (importJsonContent.isNotBlank()) {
                                    viewModel.importProfileFromJson(importJsonContent) { success, result ->
                                        if (success) {
                                            Toast.makeText(context, "Successfully loaded '$result' profile!", Toast.LENGTH_LONG).show()
                                            showImportDialog = false
                                            importJsonContent = ""
                                            dummyRecomposeTrigger++
                                        } else {
                                            Toast.makeText(context, "Error: $result", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF381E72),
                                contentColor = Color(0xFFD0BCFF)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("LOAD PROFILE", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MappedLayoutEditor(
    viewModel: KeymapperViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val buttons by viewModel.editedButtons.collectAsState()
    val activeProfile by viewModel.selectedProfile.collectAsState()

    var activeDialogButton by remember { mutableStateOf<MappedButton?>(null) }
    
    var showAddButtonDialog by remember { mutableStateOf(false) }
    var newBtnLabel by remember { mutableStateOf("") }
    var newBtnChar by remember { mutableStateOf("F") }
    var newBtnCode by remember { mutableStateOf(KeyEvent.KEYCODE_F) }
    var newBtnType by remember { mutableStateOf(ButtonType.TAP) }

    var selectedBgIndex by remember { mutableStateOf(1) } // Default to battlefield shooter template
    var showGridLines by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top action headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF381E72))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFFD0BCFF))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "EDITING LAYOUT MAP: " + (activeProfile?.name?.uppercase() ?: ""),
                    fontSize = 12.sp,
                    color = Color(0xFFD0BCFF),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF381E72))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "CHROMEBOOK LINKED 💻",
                            color = Color(0xFFD0BCFF),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.saveAllEditedButtons(context)
                    Toast.makeText(context, "Coordinates and Bindings Sync Saved!", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF381E72),
                    contentColor = Color(0xFFD0BCFF)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD0BCFF)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("editor_save_btn")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(16.dp), tint = Color(0xFFD0BCFF))
                    Text(" SAVE", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Background preset selection dashboard bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SCREEN CANVAS UNDERLAY:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
            listOf("SLATE GRID", "FPS GAME HUD", "RPG COMBAT HUD").forEachIndexed { index, name ->
                val isSelected = selectedBgIndex == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF381E72) else Color(0xFF1C1B1F))
                        .border(1.dp, if (isSelected) Color(0xFFD0BCFF) else Color(0xFF49454F), RoundedCornerShape(8.dp))
                        .clickable { selectedBgIndex = index }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = name,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color(0xFFE6E1E9) else Color(0xFF938F99),
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (showGridLines) Color(0xFF381E72) else Color(0xFF1C1B1F))
                    .border(1.dp, if (showGridLines) Color(0xFFD0BCFF) else Color(0xFF49454F), RoundedCornerShape(8.dp))
                    .clickable { showGridLines = !showGridLines }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (showGridLines) "GRID: ON" else "GRID: OFF",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (showGridLines) Color(0xFFD0BCFF) else Color(0xFF938F99),
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Large simulated tablet/phone screen mapping sandbox!
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .border(1.dp, Color(0xFF49454F).copy(alpha = 0.6f), RoundedCornerShape(32.dp))
                .background(Color(0xFF000000)) // Pitch Black slate sandbox
        ) {
            val densityLocal = LocalDensity.current
            val parentWidthDp = maxWidth
            val parentHeightDp = maxHeight

            // 1. Grid matrix overlay lines
            if (showGridLines) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val color = Color(0xFFD0BCFF).copy(alpha = 0.08f)
                    val width = size.width
                    val height = size.height
                    
                    // Vertical grids
                    val stepsX = 12
                    for (i in 1 until stepsX) {
                        val x = (width / stepsX) * i
                        drawLine(color, start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, height), strokeWidth = 1f)
                    }
                    
                    // Horizontal grids
                    val stepsY = 8
                    for (i in 1 until stepsY) {
                        val y = (height / stepsY) * i
                        drawLine(color, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(width, y), strokeWidth = 1f)
                    }
                    
                    // Axis alignments
                    drawLine(Color(0xFFD0BCFF).copy(alpha = 0.25f), start = androidx.compose.ui.geometry.Offset(width / 2f, 0f), end = androidx.compose.ui.geometry.Offset(width / 2f, height), strokeWidth = 1.5f)
                    drawLine(Color(0xFFD0BCFF).copy(alpha = 0.25f), start = androidx.compose.ui.geometry.Offset(0f, height / 2f), end = androidx.compose.ui.geometry.Offset(width, height / 2f), strokeWidth = 1.5f)
                }
            }

            // 2. Mock game underlays
            when (selectedBgIndex) {
                1 -> { // SHOOTER HUD (PUBG/COD Mobile Style)
                    // Movement stick bottom left
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 54.dp, bottom = 48.dp)
                            .size(110.dp)
                            .border(1.5.dp, Color(0xFFD0BCFF).copy(alpha = 0.25f), CircleShape)
                            .background(Color(0xFF1C1B1F).copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(36.dp).background(Color(0xFFD0BCFF).copy(alpha = 0.35f), CircleShape))
                        Text("WASD", color = Color(0xFFD0BCFF).copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                    }

                    // Fire buttons group bottom right
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 40.dp, bottom = 110.dp)
                            .size(60.dp)
                            .border(1.5.dp, Color(0xFFBA1A1A).copy(alpha = 0.5f), CircleShape)
                            .background(Color(0xFFBA1A1A).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color(0xFFE6E1E9).copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                    }

                    // Crouch button
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 115.dp, bottom = 36.dp)
                            .size(44.dp)
                            .border(1.dp, Color(0xFFE6E1E9).copy(alpha = 0.2f), CircleShape)
                            .background(Color(0xFF1C1B1F).copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("CROUCH", color = Color(0xFFD0BCFF).copy(alpha = 0.6f), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                    }

                    // Jump button
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 44.dp, bottom = 36.dp)
                            .size(48.dp)
                            .border(1.dp, Color(0xFFE6E1E9).copy(alpha = 0.2f), CircleShape)
                            .background(Color(0xFF1C1B1F).copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("JUMP", color = Color(0xFFD0BCFF).copy(alpha = 0.6f), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                    }

                    // Map radar top right
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(24.dp)
                            .size(76.dp)
                            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(12.dp))
                            .background(Color(0xFF1C1B1F).copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("MINI MAP", color = Color(0xFF938F99), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                    }
                }
                2 -> { // RPG COMBAT HUD (Genshin style)
                    // D-Pad Stick
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 64.dp, bottom = 64.dp)
                            .size(100.dp)
                            .border(1.5.dp, Color(0xFFD0BCFF).copy(alpha = 0.2f), CircleShape)
                            .background(Color(0xFF1C1B1F).copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(32.dp).background(Color(0xFFD0BCFF).copy(alpha = 0.3f), CircleShape))
                    }

                    // Main Attack Button
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 64.dp, bottom = 64.dp)
                            .size(76.dp)
                            .border(2.dp, Color(0xFFD0BCFF).copy(alpha = 0.4f), CircleShape)
                            .background(Color(0xFF381E72).copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("ATTACK", color = Color(0xFFE6E1E9).copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                    }

                    // Skill 1 (Skill)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 150.dp, bottom = 78.dp)
                            .size(44.dp)
                            .border(1.dp, Color(0xFFE6E1E9).copy(alpha = 0.2f), CircleShape)
                            .background(Color(0xFF1C1B1F).copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("SKILL", color = Color(0xFFD0BCFF).copy(alpha = 0.7f), fontSize = 8.sp, fontFamily = FontFamily.SansSerif)
                    }

                    // Skill 2 (Ultimate Burst)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 80.dp, bottom = 150.dp)
                            .size(44.dp)
                            .border(1.dp, Color(0xFFE6E1E9).copy(alpha = 0.2f), CircleShape)
                            .background(Color(0xFF1C1B1F).copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("BURST", color = Color(0xFFD0BCFF).copy(alpha = 0.7f), fontSize = 8.sp, fontFamily = FontFamily.SansSerif)
                    }
                }
                else -> { // DEFAULT METRIC GRID lines
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "SCREEN PORTAL SIMULATOR", 
                                fontSize = 11.sp, 
                                fontWeight = FontWeight.Bold, 
                                color = Color(0xFFD0BCFF), 
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                "Move overlay trigger badges exactly where your game control buttons rest", 
                                fontSize = 10.sp, 
                                color = Color(0xFF938F99),
                                textAlign = TextAlign.Center,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }

            // Draggable buttons array (Pixel-Perfect DP-relative Translation)
            buttons.forEach { button ->
                val buttonSizeDp = (button.sizeDp * 0.45f).coerceIn(40f, 160f)
                val halfSizeDp = buttonSizeDp / 2f

                Box(
                    modifier = Modifier
                        .size(buttonSizeDp.dp)
                        .offset(
                            x = (button.xPercent * parentWidthDp.value - halfSizeDp).dp,
                            y = (button.yPercent * parentHeightDp.value - halfSizeDp).dp
                        )
                        .pointerInput(button) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                // Convert dragAmount (pixels) to DP:
                                val dragAmountXDp = with(densityLocal) { dragAmount.x.toDp().value }
                                val dragAmountYDp = with(densityLocal) { dragAmount.y.toDp().value }
                                
                                val newX = (button.xPercent * parentWidthDp.value + dragAmountXDp) / parentWidthDp.value
                                val newY = (button.yPercent * parentHeightDp.value + dragAmountYDp) / parentHeightDp.value
                                
                                val updatedList = buttons.map {
                                    if (it.id == button.id) {
                                        it.copy(
                                            xPercent = newX.coerceIn(0.01f, 0.99f),
                                            yPercent = newY.coerceIn(0.01f, 0.99f)
                                        )
                                    } else it
                                }
                                viewModel.updateEditedButtonPositions(updatedList)
                            }
                        }
                        .clip(CircleShape)
                        .background(
                            if (button.type == ButtonType.LOOK_AIM) 
                                Color(0xFF381E72) 
                            else 
                                Color(0xFF2B2930)
                        )
                        .border(
                            1.dp,
                            if (button.type == ButtonType.LOOK_AIM) Color(0xFFD0BCFF) else Color(0xFF49454F),
                            CircleShape
                        )
                        .clickable { activeDialogButton = button }
                        .testTag("draggable_button_${button.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text(
                            text = button.keyChar,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE6E1E9),
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = if (button.type == ButtonType.LOOK_AIM) "LOOK" else button.label,
                            fontSize = 8.sp,
                            color = Color(0xFFD0BCFF),
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick panel buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { showAddButtonDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF381E72),
                    contentColor = Color(0xFFD0BCFF)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD0BCFF)),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("editor_add_trigger_btn"),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
                Text(" ADD VIRTUAL TRIGGER", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, color = Color(0xFFD0BCFF))
            }
        }
    }

    // Modal dialogue to Edit existing mappings or map new keyboard keys
    activeDialogButton?.let { button ->
        Dialog(onDismissRequest = { activeDialogButton = null }) {
            val keyCaptureFocusRequester = remember { FocusRequester() }
            
            var editableLabel by remember { mutableStateOf(button.label) }
            var editableKeyStr by remember { mutableStateOf(button.keyChar) }
            var editableCode by remember { mutableStateOf(button.keyCode) }
            var touchSize by remember { mutableStateOf(button.sizeDp.toFloat()) }
            var isListeningForEditKey by remember { mutableStateOf(false) }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(28.dp))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "MODIFY TRIGGER PROPERTIES",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6E1E9),
                        fontFamily = FontFamily.SansSerif
                    )

                    OutlinedTextField(
                        value = editableLabel,
                        onValueChange = { editableLabel = it },
                        label = { Text("Label (e.g. Reload)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE6E1E9),
                            unfocusedTextColor = Color(0xFF938F99),
                            focusedBorderColor = Color(0xFFD0BCFF),
                            unfocusedBorderColor = Color(0xFF49454F),
                            focusedLabelColor = Color(0xFFD0BCFF),
                            unfocusedLabelColor = Color(0xFF938F99)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_trigger_label_input")
                    )

                    // Keyboard Binder Trigger Card with active focus key listener
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isListeningForEditKey) Color(0xFF381E72) else Color(0xFF1C1B1F))
                            .border(
                                2.dp, 
                                if (isListeningForEditKey) Color(0xFFD0BCFF) else Color(0xFF49454F), 
                                RoundedCornerShape(12.dp)
                            )
                            .focusRequester(keyCaptureFocusRequester)
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                if (isListeningForEditKey && keyEvent.type == KeyEventType.KeyDown) {
                                    val nativeCode = keyEvent.nativeKeyEvent.keyCode
                                    val keyCharName = try {
                                        KeyEvent.keyCodeToString(nativeCode)
                                            .replace("KEYCODE_", "")
                                            .replace("BUTTON_", "")
                                    } catch (e: Exception) {
                                        "KEY_$nativeCode"
                                    }
                                    editableKeyStr = keyCharName
                                    editableCode = nativeCode
                                    isListeningForEditKey = false
                                    true
                                } else false
                            }
                            .clickable { 
                                isListeningForEditKey = !isListeningForEditKey 
                            }
                            .padding(14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isListeningForEditKey) "🔴 RECORDING..." else "PHYSICAL KEYBINDING TARGET", 
                                    fontSize = 11.sp, 
                                    color = if (isListeningForEditKey) Color(0xFFEF9A9A) else Color(0xFF938F99), 
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isListeningForEditKey) "PRESS ANY KEY NOW!" else "$editableKeyStr (Code: $editableCode)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isListeningForEditKey) Color(0xFFEF9A9A) else Color(0xFFD0BCFF),
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                            Icon(
                                imageVector = if (isListeningForEditKey) Icons.Default.Adjust else Icons.Default.Keyboard, 
                                contentDescription = "Bind Key", 
                                tint = if (isListeningForEditKey) Color(0xFFEF9A9A) else Color(0xFF938F99)
                            )
                        }
                    }

                    LaunchedEffect(isListeningForEditKey) {
                        if (isListeningForEditKey) {
                            try {
                                keyCaptureFocusRequester.requestFocus()
                            } catch (e: Exception) {
                                // safe fallback
                            }
                        }
                    }

                    if (isListeningForEditKey) {
                        Text(
                            "CLICK THE BOX ABOVE, THEN PRESS ANY PHYSICAL KEY",
                            fontSize = 10.sp,
                            color = Color(0xFFD0BCFF),
                            fontFamily = FontFamily.SansSerif,
                            textAlign = TextAlign.Center
                        )
                        // Mock binds selector row so users can easily test keymapping adjustments in visual environments easily!
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("SPACE", "R", "C", "SHIFT", "Q", "E", "F").forEach { mockKey ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1C1B1F))
                                        .border(1.dp, Color(0xFF49454F), RoundedCornerShape(8.dp))
                                        .clickable {
                                            editableKeyStr = mockKey
                                            editableCode = when (mockKey) {
                                                "SPACE" -> KeyEvent.KEYCODE_SPACE
                                                "R" -> KeyEvent.KEYCODE_R
                                                "C" -> KeyEvent.KEYCODE_C
                                                "SHIFT" -> KeyEvent.KEYCODE_SHIFT_LEFT
                                                "Q" -> KeyEvent.KEYCODE_Q
                                                "E" -> KeyEvent.KEYCODE_E
                                                else -> KeyEvent.KEYCODE_F
                                            }
                                            isListeningForEditKey = false
                                        }
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(mockKey, fontSize = 9.sp, color = Color(0xFFD0BCFF), fontFamily = FontFamily.SansSerif)
                                }
                            }
                        }
                    }

                    // Size controller
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TOUCH RADIAL SIZE (DP)", fontSize = 11.sp, color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                            Text("${touchSize.toInt()} dp", color = Color(0xFFD0BCFF), fontFamily = FontFamily.SansSerif)
                        }
                        Slider(
                            value = touchSize,
                            onValueChange = { touchSize = it },
                            valueRange = 36f..300f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFD0BCFF),
                                activeTrackColor = Color(0xFFD0BCFF),
                                inactiveTrackColor = Color(0xFF49454F)
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Delete individual trigger button
                        Button(
                            onClick = {
                                viewModel.deleteButtonFromProfile(button)
                                activeDialogButton = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBA1A1A)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { activeDialogButton = null }) {
                                Text("CANCEL", color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    val updated = button.copy(
                                        label = editableLabel,
                                        keyChar = editableKeyStr,
                                        keyCode = editableCode,
                                        sizeDp = touchSize.toInt()
                                    )
                                    viewModel.addNewButtonToProfile(updated)
                                    activeDialogButton = null
                                    Toast.makeText(context, "Trigger config scheduled!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF381E72),
                                    contentColor = Color(0xFFD0BCFF)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD0BCFF)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("edit_trigger_save_btn")
                            ) {
                                Text("CONFIRM", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                            }
                        }
                    }
                }
            }
        }
        // Modal adding new individual trigger badges
    if (showAddButtonDialog) {
        Dialog(onDismissRequest = { showAddButtonDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(28.dp))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "ADD NEW OVERLAY TRIGGER",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6E1E9),
                        fontFamily = FontFamily.SansSerif
                    )

                    OutlinedTextField(
                        value = newBtnLabel,
                        onValueChange = { newBtnLabel = it },
                        label = { Text("Trigger Action (e.g. Crouch)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE6E1E9),
                            unfocusedTextColor = Color(0xFF938F99),
                            focusedBorderColor = Color(0xFFD0BCFF),
                            unfocusedBorderColor = Color(0xFF49454F),
                            focusedLabelColor = Color(0xFFD0BCFF),
                            unfocusedLabelColor = Color(0xFF938F99)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_trigger_label_input")
                    )

                    val addKeyCaptureFocusRequester = remember { FocusRequester() }
                    var isListeningForAddKey by remember { mutableStateOf(false) }

                    // Keyboard Binder Trigger Card with active focus key listener
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isListeningForAddKey) Color(0xFF381E72) else Color(0xFF1C1B1F))
                            .border(
                                2.dp, 
                                if (isListeningForAddKey) Color(0xFFD0BCFF) else Color(0xFF49454F), 
                                RoundedCornerShape(12.dp)
                            )
                            .focusRequester(addKeyCaptureFocusRequester)
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                if (isListeningForAddKey && keyEvent.type == KeyEventType.KeyDown) {
                                    val nativeCode = keyEvent.nativeKeyEvent.keyCode
                                    val keyCharName = try {
                                        KeyEvent.keyCodeToString(nativeCode)
                                            .replace("KEYCODE_", "")
                                            .replace("BUTTON_", "")
                                    } catch (e: Exception) {
                                        "KEY_$nativeCode"
                                    }
                                    newBtnChar = keyCharName
                                    newBtnCode = nativeCode
                                    isListeningForAddKey = false
                                    true
                                } else false
                            }
                            .clickable { 
                                isListeningForAddKey = !isListeningForAddKey 
                            }
                            .padding(14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isListeningForAddKey) "🔴 RECORDING..." else "PHYSICAL KEYBINDING TARGET", 
                                    fontSize = 11.sp, 
                                    color = if (isListeningForAddKey) Color(0xFFEF9A9A) else Color(0xFF938F99), 
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isListeningForAddKey) "PRESS ANY KEY NOW!" else "$newBtnChar (Code: $newBtnCode)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isListeningForAddKey) Color(0xFFEF9A9A) else Color(0xFFD0BCFF),
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                            Icon(
                                imageVector = if (isListeningForAddKey) Icons.Default.Adjust else Icons.Default.Keyboard, 
                                contentDescription = "Bind Key", 
                                tint = if (isListeningForAddKey) Color(0xFFEF9A9A) else Color(0xFF938F99)
                            )
                        }
                    }

                    LaunchedEffect(isListeningForAddKey) {
                        if (isListeningForAddKey) {
                            try {
                                addKeyCaptureFocusRequester.requestFocus()
                            } catch (e: Exception) {
                                // safe fallback
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newBtnChar,
                        onValueChange = { newBtnChar = it },
                        label = { Text("Bound Key Character (e.g. G)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE6E1E9),
                            unfocusedTextColor = Color(0xFF938F99),
                            focusedBorderColor = Color(0xFFD0BCFF),
                            unfocusedBorderColor = Color(0xFF49454F),
                            focusedLabelColor = Color(0xFFD0BCFF),
                            unfocusedLabelColor = Color(0xFF938F99)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_trigger_key_input")
                    )

                    // Macro sequences
                    var macroSeqStr by remember { mutableStateOf("") }
                    if (newBtnType == ButtonType.MACRO) {
                        OutlinedTextField(
                            value = macroSeqStr,
                            onValueChange = { macroSeqStr = it },
                            label = { Text("Combo (e.g. tap_0.8_0.8;delay_100;tap_0.5_0.5)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFFE6E1E9),
                                unfocusedTextColor = Color(0xFF938F99),
                                focusedBorderColor = Color(0xFFD0BCFF),
                                unfocusedBorderColor = Color(0xFF49454F),
                                focusedLabelColor = Color(0xFFD0BCFF),
                                unfocusedLabelColor = Color(0xFF938F99)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Mapping Button Action Type selectors
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(ButtonType.TAP, ButtonType.LOOK_AIM, ButtonType.MACRO).forEach { type ->
                            val isSelected = newBtnType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0xFF381E72) else Color(0xFF1C1B1F))
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFFD0BCFF) else Color(0xFF49454F),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { newBtnType = type }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = type.name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color(0xFFD0BCFF) else Color(0xFF938F99),
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showAddButtonDialog = false }) {
                            Text("CLOSE", color = Color(0xFF938F99), fontFamily = FontFamily.SansSerif)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (newBtnLabel.isNotBlank() && newBtnChar.isNotBlank()) {
                                    val profileId = activeProfile?.id ?: return@Button
                                    
                                    // Get key code map simple helper
                                    val code = if (newBtnChar.uppercase() == "LCLICK") 1 
                                               else if (newBtnChar.uppercase() == "RCLICK") 2 
                                               else KeyEvent.keyCodeFromString("KEYCODE_" + newBtnChar.uppercase())
                                               
                                    val mappedBtn = MappedButton(
                                        profileId = profileId,
                                        label = newBtnLabel,
                                        keyChar = newBtnChar.uppercase(),
                                        keyCode = if (code != 0) code else KeyEvent.KEYCODE_UNKNOWN,
                                        type = newBtnType,
                                        xPercent = 0.50f,
                                        yPercent = 0.50f,
                                        macroSequence = macroSeqStr
                                    )
                                    viewModel.addNewButtonToProfile(mappedBtn)
                                    newBtnLabel = ""
                                    newBtnChar = "F"
                                    showAddButtonDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF381E72),
                                contentColor = Color(0xFFD0BCFF)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD0BCFF)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("add_trigger_confirm_btn")
                        ) {
                            Text("ADD TRIGGER", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        }
                    }
                }
            }
        }
    }  }
}
