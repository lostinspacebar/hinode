package com.lostinspacebar.hinode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

import com.lostinspacebar.hinode.ui.auth.AuthUiState
import com.lostinspacebar.hinode.ui.auth.AuthViewModel
import com.lostinspacebar.hinode.ui.auth.LoginScreen
import com.lostinspacebar.hinode.ui.chat.ChatScreen
import com.lostinspacebar.hinode.ui.chat.ChatViewModel
import hinode.composeapp.generated.resources.Res
import hinode.composeapp.generated.resources.compose_multiplatform

// Custom white and gray color scheme
private val WhiteGrayColorScheme = lightColorScheme(
    primary = Color(0xFF2C2C2C),           // Dark gray for primary elements
    onPrimary = Color.White,                // White text on primary
    primaryContainer = Color(0xFFF5F5F5),   // Very light gray
    onPrimaryContainer = Color(0xFF1C1C1C), // Almost black

    secondary = Color(0xFF5C5C5C),          // Medium gray
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E8E8), // Light gray
    onSecondaryContainer = Color(0xFF2C2C2C),

    tertiary = Color(0xFFE63946),           // Red accent (sunrise theme)
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDADD),
    onTertiaryContainer = Color(0xFF8C1D23),

    background = Color.White,               // White background
    onBackground = Color(0xFF1C1C1C),       // Almost black text

    surface = Color.White,                  // White surface
    onSurface = Color(0xFF1C1C1C),          // Almost black text
    surfaceVariant = Color(0xFFF5F5F5),     // Very light gray
    onSurfaceVariant = Color(0xFF3C3C3C),   // Dark gray

    outline = Color(0xFFD0D0D0),            // Light gray outline
    outlineVariant = Color(0xFFE8E8E8),     // Very light gray outline

    error = Color(0xFFE63946),              // Red for errors
    onError = Color.White,
    errorContainer = Color(0xFFFFDADD),
    onErrorContainer = Color(0xFF8C1D23),
)

// Custom shapes with reduced corner radius
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(1.dp),      // Buttons use this
    extraLarge = RoundedCornerShape(6.dp)
)

@Composable
fun SunriseLogo(modifier: Modifier = Modifier, size: Int = 32) {
    Canvas(modifier = modifier.size(size.dp)) {
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val radius = this.size.minDimension / 2

        // White background circle
        drawCircle(
            color = Color.White,
            radius = radius,
            center = center,
            style = Fill
        )

        // Red sun (semicircle at bottom representing sunrise)
        val sunRadius = radius * 0.4f
        val sunCenter = Offset(center.x, center.y + radius * 0.2f)

        drawCircle(
            color = Color(0xFFE63946), // Red color
            radius = sunRadius,
            center = sunCenter,
            style = Fill
        )

        // Sun rays
        val rayCount = 8
        val rayLength = radius * 0.3f
        val rayStartRadius = sunRadius + radius * 0.05f

        for (i in 0 until rayCount) {
            val angle = (i * 2 * PI / rayCount) - PI / 2 // Start from top
            val startX = sunCenter.x + cos(angle).toFloat() * rayStartRadius
            val startY = sunCenter.y + sin(angle).toFloat() * rayStartRadius
            val endX = sunCenter.x + cos(angle).toFloat() * (rayStartRadius + rayLength)
            val endY = sunCenter.y + sin(angle).toFloat() * (rayStartRadius + rayLength)

            drawLine(
                color = Color(0xFFE63946),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 2f
            )
        }
    }
}

@Composable
@Preview
fun App() {
    // Initialize repository and viewmodel
    val repository = remember { createMatrixRepository() }
    val authViewModel = remember { AuthViewModel(repository) }
    val authState by authViewModel.uiState.collectAsState()

    MaterialTheme(
        colorScheme = WhiteGrayColorScheme,
        shapes = AppShapes
    ) {
        // Show login screen if not logged in, otherwise show main app
        when (authState) {
            AuthUiState.LoggedOut, AuthUiState.Loading -> {
                LoginScreen(viewModel = authViewModel)
            }
            AuthUiState.LoggedIn -> {
                MainAppContent(
                    authViewModel = authViewModel,
                    repository = repository
                )
            }
        }
    }
}

/**
 * Main app content (shown when logged in)
 */
@Composable
private fun MainAppContent(
    authViewModel: AuthViewModel,
    repository: com.lostinspacebar.hinode.data.TrixnityMatrixRepository
) {
    val chatViewModel = remember { ChatViewModel(repository) }
    MaterialTheme(
        colorScheme = WhiteGrayColorScheme,
        shapes = AppShapes
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
        ) {
            val density = LocalDensity.current

            // Determine if we're on a large screen (laptop/tablet) or small screen (phone)
            val isLargeScreen = maxWidth >= 600.dp

            // Sidebar state - open by default on large screens, closed on small screens
            var isSidebarOpen by remember { mutableStateOf(isLargeScreen) }

            // Sidebar width state with min/max constraints
            val minSidebarWidth = 150.dp
            val maxSidebarWidth = 500.dp
            val collapsedSidebarWidth = 64.dp
            val defaultWidth = if (isLargeScreen) 250.dp else 200.dp
            var sidebarWidth by remember { mutableStateOf(defaultWidth) }

            // Interaction source for sidebar title hover
            val titleInteractionSource = remember { MutableInteractionSource() }
            val isTitleHovered by titleInteractionSource.collectIsHoveredAsState()

            // Update sidebar state when screen size changes
            LaunchedEffect(isLargeScreen) {
                isSidebarOpen = isLargeScreen
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Sidebar - always visible, either expanded or collapsed
                Row(modifier = Modifier.fillMaxHeight()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(if (isSidebarOpen) sidebarWidth else collapsedSidebarWidth),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 2.dp
                    ) {
                        if (isSidebarOpen) {
                            // Expanded sidebar with full content
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Header with hamburger button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(onClick = { isSidebarOpen = false }) {
                                        SunriseLogo(size = 32)
                                    }
                                    Box(
                                        modifier = Modifier.hoverable(
                                            interactionSource = titleInteractionSource
                                        )
                                    ) {
                                        Text(
                                            if (isTitleHovered) "hinode" else "日出",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                HorizontalDivider()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("🏠", style = MaterialTheme.typography.titleLarge)
                                    Text("Menu Item 1")
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("⚙️", style = MaterialTheme.typography.titleLarge)
                                    Text("Menu Item 2")
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("📊", style = MaterialTheme.typography.titleLarge)
                                    Text("Menu Item 3")
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                // Logout button
                                HorizontalDivider()
                                TextButton(
                                    onClick = { authViewModel.logout() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text("🚪", style = MaterialTheme.typography.titleLarge)
                                        Text("Logout")
                                    }
                                }
                            }
                        } else {
                            // Collapsed sidebar with icons only
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Hamburger button
                                IconButton(onClick = { isSidebarOpen = true }) {
                                    SunriseLogo(size = 32)
                                }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                                // Icon-only menu items
                                IconButton(onClick = { /* Menu Item 1 action */ }) {
                                    Text("🏠", style = MaterialTheme.typography.titleLarge)
                                }
                                IconButton(onClick = { /* Menu Item 2 action */ }) {
                                    Text("⚙️", style = MaterialTheme.typography.titleLarge)
                                }
                                IconButton(onClick = { /* Menu Item 3 action */ }) {
                                    Text("📊", style = MaterialTheme.typography.titleLarge)
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                // Logout button (icon only)
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                                IconButton(onClick = { authViewModel.logout() }) {
                                    Text("🚪", style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                    }

                    // Resizable divider - only show when sidebar is open
                    if (isSidebarOpen) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(4.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val newWidthPx = with(density) { sidebarWidth.toPx() } + dragAmount.x
                                        val newWidth = with(density) { newWidthPx.toDp() }
                                        sidebarWidth = newWidth.coerceIn(minSidebarWidth, maxSidebarWidth)
                                    }
                                }
                        )
                    }
                }

                // Main Content Area - Chat Screen
                ChatScreen(
                    viewModel = chatViewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        }
    }
}
