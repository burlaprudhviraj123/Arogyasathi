package org.arogyasathi.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.tooling.preview.Preview

data class NavigationTabItem(
    val title: String,
    val icon: String,
    val index: Int
)

@Composable
fun FloatingGlassBottomBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavigationTabItem("Today", "📅", 0),
        NavigationTabItem("Families", "👥", 1),
        NavigationTabItem("Assistant", "🩺", 2),
        NavigationTabItem("Records", "📁", 3)
    )

    val isDark = isSystemInDarkTheme()
    val glassBg = if (isDark) DarkGlassBackground else GlassBackground
    val glassBrd = if (isDark) DarkGlassBorder else GlassBorder
    val activeContainer = if (isDark) Color(0xFF133E3A) else Color(0xFFE6F4F1)
    val activeTint = if (isDark) Color(0xFF14B8A6) else PrimaryTeal

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
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
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = selectedIndex == item.index
                    val animBg by animateColorAsState(
                        targetValue = if (isSelected) activeContainer else Color.Transparent,
                        label = "tabBg"
                    )

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = animBg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onTabSelected(item.index) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.icon,
                                fontSize = 16.sp
                            )
                            if (isSelected) {
                                Text(
                                    text = item.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = activeTint
                                )
                            }
                        }
                    }
                }
            }
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
