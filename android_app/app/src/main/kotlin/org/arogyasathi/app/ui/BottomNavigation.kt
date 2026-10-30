package org.arogyasathi.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.arogyasathi.app.R

data class NavigationTabItem(
    val title: String,
    val iconRes: Int,
    val index: Int
)

@Composable
fun FloatingGlassBottomBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val items = listOf(
        NavigationTabItem(strings.tabToday, R.drawable.ic_today, 0),
        NavigationTabItem(strings.tabFamilies, R.drawable.ic_families, 1),
        NavigationTabItem(strings.tabAssistant, R.drawable.ic_assistant, 2),
        NavigationTabItem(strings.tabRecords, R.drawable.ic_records, 3),
        NavigationTabItem(strings.tabSettings, R.drawable.ic_settings, 4)
    )

    val isDark = isSystemInDarkTheme()
    val glassBg = if (isDark) DarkGlassBackground else GlassBackground
    val glassBrd = if (isDark) DarkGlassBorder else GlassBorder
    val activeContainer = if (isDark) Color(0xFF133E3A) else Color(0xFFE6F4F1)
    val activeTint = if (isDark) Color(0xFF14B8A6) else PrimaryTeal

    // Coordinate tracking for the Framer Motion spring sliding pill
    var barCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val tabCoordsMap = remember { mutableStateMapOf<Int, LayoutCoordinates>() }
    val tabBounds = remember { mutableStateMapOf<Int, Rect>() }

    fun updateBounds() {
        val bar = barCoordinates ?: return
        if (!bar.isAttached) return
        tabCoordsMap.forEach { (index, coords) ->
            if (coords.isAttached) {
                tabBounds[index] = bar.localBoundingBoxOf(coords)
            }
        }
    }

    val activeSideRect = if (selectedIndex != 2) tabBounds[selectedIndex] else null
    var lastKnownSideRect by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(activeSideRect) {
        if (activeSideRect != null) {
            lastKnownSideRect = activeSideRect
        }
    }

    val currentRect = activeSideRect ?: lastKnownSideRect

    // Spring animation physics matching Framer Motion's spring.morph (smooth, organic glide)
    val animLeft by animateFloatAsState(
        targetValue = currentRect?.left ?: 0f,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 420f
        ),
        label = "indicatorLeft"
    )
    val animTop by animateFloatAsState(
        targetValue = currentRect?.top ?: 0f,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 420f
        ),
        label = "indicatorTop"
    )
    val animWidth by animateFloatAsState(
        targetValue = currentRect?.width ?: 0f,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 420f
        ),
        label = "indicatorWidth"
    )
    val animHeight by animateFloatAsState(
        targetValue = currentRect?.height ?: 0f,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 420f
        ),
        label = "indicatorHeight"
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selectedIndex != 2 && currentRect != null) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "indicatorAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(12.dp, shape = RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .border(1.dp, glassBrd, RoundedCornerShape(32.dp)),
            color = glassBg
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp)
                    .onGloballyPositioned {
                        barCoordinates = it
                        updateBounds()
                    }
            ) {
                // Sliding indicator pill (glides smoothly across side tabs with spring dynamics)
                if (indicatorAlpha > 0.01f && currentRect != null) {
                    val density = LocalDensity.current
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(animLeft.roundToInt(), animTop.roundToInt())
                            }
                            .size(
                                width = with(density) { animWidth.toDp() },
                                height = with(density) { animHeight.toDp() }
                            )
                            .graphicsLayer { alpha = indicatorAlpha }
                            .clip(RoundedCornerShape(19.dp))
                            .background(activeContainer)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Wing: Tab 0 (Today) and Tab 1 (Families) - strictly balanced weight
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SideNavTab(
                            item = items[0],
                            isSelected = selectedIndex == 0,
                            onTabSelected = onTabSelected,
                            activeTint = activeTint,
                            onPositioned = { coords ->
                                tabCoordsMap[0] = coords
                                updateBounds()
                            }
                        )
                        SideNavTab(
                            item = items[1],
                            isSelected = selectedIndex == 1,
                            onTabSelected = onTabSelected,
                            activeTint = activeTint,
                            onPositioned = { coords ->
                                tabCoordsMap[1] = coords
                                updateBounds()
                            }
                        )
                    }

                    // Center Core Anchor: Tab 2 (Assistant) - Permanently locked at 50% dead center
                    CentralAssistantButton(
                        item = items[2],
                        isSelected = selectedIndex == 2,
                        onSelect = { onTabSelected(2) },
                        isDark = isDark
                    )

                    // Right Wing: Tab 3 (Records) and Tab 4 (Settings) - strictly balanced weight
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SideNavTab(
                            item = items[3],
                            isSelected = selectedIndex == 3,
                            onTabSelected = onTabSelected,
                            activeTint = activeTint,
                            onPositioned = { coords ->
                                tabCoordsMap[3] = coords
                                updateBounds()
                            }
                        )
                        SideNavTab(
                            item = items[4],
                            isSelected = selectedIndex == 4,
                            onTabSelected = onTabSelected,
                            activeTint = activeTint,
                            onPositioned = { coords ->
                                tabCoordsMap[4] = coords
                                updateBounds()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SideNavTab(
    item: NavigationTabItem,
    isSelected: Boolean,
    onTabSelected: (Int) -> Unit,
    activeTint: Color,
    onPositioned: (LayoutCoordinates) -> Unit
) {
    val animTint by animateColorAsState(
        targetValue = if (isSelected) activeTint else TextSecondary,
        label = "sideTabTint"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 52.dp, height = 38.dp)
            .onGloballyPositioned { onPositioned(it) }
            .clip(RoundedCornerShape(19.dp))
            .semantics {
                role = Role.Tab
                contentDescription = "${item.title} tab"
                stateDescription = if (isSelected) "Selected" else "Not selected"
            }
            .clickable { onTabSelected(item.index) }
    ) {
        Icon(
            painter = painterResource(id = item.iconRes),
            contentDescription = item.title,
            modifier = Modifier.size(20.dp),
            tint = animTint
        )
    }
}

@Composable
private fun CentralAssistantButton(
    item: NavigationTabItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    isDark: Boolean
) {
    val centerBg by animateColorAsState(
        targetValue = if (isSelected) PrimaryTeal else if (isDark) Color(0xFF133E3A) else Color(0xFFE6F4F1),
        label = "centerBg"
    )
    val centerIconTint by animateColorAsState(
        targetValue = if (isSelected) Color.White else PrimaryTeal,
        label = "centerIconTint"
    )
    val centerScale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "centerScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .semantics {
                role = Role.Tab
                contentDescription = "${item.title} tab"
                stateDescription = if (isSelected) "Selected" else "Not selected"
            }
    ) {
        Surface(
            modifier = Modifier
                .size(50.dp)
                .graphicsLayer {
                    scaleX = centerScale
                    scaleY = centerScale
                }
                .shadow(if (isSelected) 8.dp else 2.dp, CircleShape)
                .clip(CircleShape)
                .clickable { onSelect() },
            shape = CircleShape,
            color = centerBg,
            border = BorderStroke(
                width = if (isSelected) 2.dp else 1.5.dp,
                color = if (isSelected) Color(0xFF2DD4BF) else PrimaryTeal.copy(alpha = 0.45f)
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = item.iconRes),
                    contentDescription = item.title,
                    modifier = Modifier.size(24.dp),
                    tint = centerIconTint
                )
            }
        }

        // Active indicator dot at the bottom of the circular button (matching reference UI)
        AnimatedVisibility(
            visible = isSelected,
            enter = scaleIn(
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 3.dp)
        ) {
            Surface(
                modifier = Modifier.size(6.dp),
                shape = CircleShape,
                color = Color(0xFF2DD4BF),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF0F172A) else Color.White)
            ) {}
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FloatingGlassBottomBarPreview() {
    ArogyasathiTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(CanvasBackground),
            contentAlignment = Alignment.Center
        ) {
            FloatingGlassBottomBar(
                selectedIndex = 0,
                onTabSelected = {}
            )
        }
    }
}
