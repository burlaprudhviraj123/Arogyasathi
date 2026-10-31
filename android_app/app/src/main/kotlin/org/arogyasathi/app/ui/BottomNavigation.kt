package org.arogyasathi.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

@Composable
fun FloatingGlassBottomBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    todayBadgeCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val items = listOf(
        NavigationTabItem(strings.tabToday,     R.drawable.ic_today,     0, badgeCount = if (todayBadgeCount > 0) todayBadgeCount else null),
        NavigationTabItem(strings.tabFamilies,  R.drawable.ic_families,  1),
        NavigationTabItem(strings.tabAssistant, R.drawable.ic_assistant, 2),
        NavigationTabItem(strings.tabRecords,   R.drawable.ic_records,   3),
        NavigationTabItem(strings.tabSettings,  R.drawable.ic_settings,  4)
    )

    val isDark = isSystemInDarkTheme()
    val barBg  = if (isDark) Color(0xFF1E293B) else Color.White
    val barBrd = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val activeContainer = if (isDark) Color(0xFF133E3A) else Color(0xFFE6F4F1)
    val activeTint      = if (isDark) Color(0xFF14B8A6) else PrimaryTeal

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
                .border(1.dp, barBrd, RoundedCornerShape(32.dp)),
            color = barBg
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left wing: Today + Families
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SideNavTab(items[0], selectedIndex == 0, onTabSelected, activeContainer, activeTint)
                    SideNavTab(items[1], selectedIndex == 1, onTabSelected, activeContainer, activeTint)
                }

                // Centre: Assistant — always locked at 50%
                CentralAssistantButton(
                    item     = items[2],
                    isSelected = selectedIndex == 2,
                    onSelect = { onTabSelected(2) },
                    isDark   = isDark
                )

                // Right wing: Records + Settings
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SideNavTab(items[3], selectedIndex == 3, onTabSelected, activeContainer, activeTint)
                    SideNavTab(items[4], selectedIndex == 4, onTabSelected, activeContainer, activeTint)
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
    activeContainer: Color,
    activeTint: Color
) {
    val animBg   by animateColorAsState(if (isSelected) activeContainer else Color.Transparent, label = "sideTabBg")
    val animTint by animateColorAsState(if (isSelected) activeTint else TextSecondary, label = "sideTabTint")
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.10f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "sideTabScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 52.dp, height = 40.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(animBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTabSelected(item.index) }
            .semantics {
                role = Role.Tab
                contentDescription = "${item.title} tab"
                stateDescription = if (isSelected) "Selected" else "Not selected"
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(id = item.iconRes),
                contentDescription = item.title,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                tint = animTint
            )
            // Badge
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
                .size(52.dp)
                .shadow(if (isSelected) 10.dp else 3.dp, CircleShape)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onSelect() },
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
                    modifier = Modifier.size(26.dp),
                    tint = centerIconTint
                )
            }
        }

        // Active dot below the circle
        if (isSelected) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 3.dp)
                    .size(6.dp),
                shape = CircleShape,
                color = Color(0xFF2DD4BF),
                border = BorderStroke(1.dp, Color.White)
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
