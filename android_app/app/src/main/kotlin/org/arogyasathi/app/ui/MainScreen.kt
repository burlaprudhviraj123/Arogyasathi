package org.arogyasathi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.arogyasathi.app.data.ArogyasathiRepository

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val repository = remember { ArogyasathiRepository(context) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var assistantQuery by remember { mutableStateOf<String?>(null) }

    Scaffold(
        bottomBar = {
            FloatingGlassBottomBar(
                selectedIndex = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CanvasBackground)
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> TodayScreen(
                    repository = repository,
                    onNavigateToAssistant = { prompt ->
                        assistantQuery = prompt
                        selectedTab = 2 // Switch to Assistant tab
                    }
                )
                1 -> FamiliesScreen(
                    repository = repository,
                    onConsultMember = { member ->
                        val prompt = when {
                            member.isPregnant -> "High risk pregnancy follow-up for ${member.name} EDD ${member.edd ?: "unknown"}"
                            member.ncdCondition != null -> "NCD management protocol for ${member.ncdCondition}"
                            else -> "Clinical protocol guidance for ${member.name}"
                        }
                        assistantQuery = prompt
                        selectedTab = 2 // Switch to Assistant tab
                    }
                )
                2 -> AssistantScreen(
                    repository = repository,
                    initialQuery = assistantQuery
                )
                3 -> RecordsScreen(
                    repository = repository
                )
            }
        }
    }
}
