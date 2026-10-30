package org.arogyasathi.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arogyasathi.app.R

data class NavigationTabItem(
    val title: String,
    val iconRes: Int,
    val index: Int,
    val badgeCount: Int? = null
)

/**
 * Floating stadium navigation bar inspired by Radix UI & Framer Motion tabs.
 * Features a gliding spring selection capsule pill, tactile icon bounce,
 * dark notification count badges, and seamless transitions across all tabs.
 */
@Composable
fun FloatingGlassBottomBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    todayBadgeCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val items = listOf(
        NavigationTabItem(strings.tabToday, R.drawable.ic_today, 0, badgeCount = if (todayBadgeCount > 0) todayBadgeCount else null),
        NavigationTabItem(strings.tabFamilies, R.drawable.ic_families, 1),
        NavigationTabItem(strings.tabAssistant, R.drawable.ic_assistant, 2),
        NavigationTabItem(strings.tabRecords, R.drawable.ic_records, 3),
        NavigationTabItem(strings.tabSettings, R.drawable.ic_settings, 4)
    )

    val isDark = isSystemInDarkTheme()
    val barBackground = if (isDark) Color(0xFF1E293B) else Color.White
    val barBorder = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val pillBackground = if (isDark) Color(0xFF334155) else Color(0xFFEFEFEF)
    val activeTint = if (isDark) Color.White else Color(0xFF0F172A)
    val inactiveTint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(percent = 50),
                    spotColor = Color(0x22000000),
                    ambientColor = Color(0x10000000)
                ),
            shape = RoundedCornerShape(percent = 50),
            color = barBackground,
            border = BorderStroke(1.dp, barBorder)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                val totalWidth = maxWidth
                val tabCount = items.size
                val tabWidth = totalWidth / tabCount
                val pillHeight = maxHeight
                val pillWidth = (tabWidth - 4.dp).coerceAtLeast(44.dp)

                // Spring animation physics matching Framer Motion's spring.morph
                val targetOffset = tabWidth * selectedIndex.coerceIn(0, tabCount - 1) + (tabWidth - pillWidth) / 2
                val animatedPillOffset by animateDpAsState(
                    targetValue = targetOffset,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = 380f
                    ),
                    label = "pillSpringOffset"
                )

                // The sliding selection capsule pill indicator
                Box(
                    modifier = Modifier
                        .offset(x = animatedPillOffset, y = 0.dp)
                        .width(pillWidth)
                        .height(pillHeight)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(pillBackground)
                )

                // The tabs row
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, item ->
                        val isSelected = selectedIndex == index
                        FloatingNavTabItem(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            item = item,
                            isSelected = isSelected,
                            activeTint = activeTint,
                            inactiveTint = inactiveTint,
                            onClick = { onTabSelected(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingNavTabItem(
    modifier: Modifier = Modifier,
    item: NavigationTabItem,
    isSelected: Boolean,
    activeTint: Color,
    inactiveTint: Color,
    onClick: () -> Unit
) {
    val animTint by animateColorAsState(
        targetValue = if (isSelected) activeTint else inactiveTint,
        animationSpec = tween(durationMillis = 200),
        label = "tabTint"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "iconScale"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, radius = 28.dp),
                onClick = onClick
            )
            .semantics {
                role = Role.Tab
                contentDescription = "${item.title} tab"
                stateDescription = if (isSelected) "Selected" else "Not selected"
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
            ) {
                Icon(
                    painter = painterResource(id = item.iconRes),
                    contentDescription = item.title,
                    modifier = Modifier.size(22.dp),
                    tint = animTint
                )

                // Dark circular notification badge at top-right of icon (matching user reference UI)
                if (item.badgeCount != null && item.badgeCount > 0) {
                    Surface(
                        color = Color(0xFF262626),
                        shape = CircleShape,
                        border = BorderStroke(1.2.dp, Color.White),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 10.dp, y = (-4).dp)
                            .size(16.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (item.badgeCount > 9) "9+" else item.badgeCount.toString(),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = item.title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = animTint,
                maxLines = 1
            )
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
