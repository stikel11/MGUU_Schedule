package com.mguuschedule.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mguuschedule.model.EducationLevel
import com.mguuschedule.model.Group
import com.mguuschedule.ui.components.ExpressiveLoadingIndicator
import com.mguuschedule.ui.theme.AppMotionScheme
import com.mguuschedule.ui.theme.ExpressiveShapes
import com.mguuschedule.util.rememberHapticFeedback

@Composable
fun OnboardingScreen(
    groupsUiState: GroupsUiState,
    onRetry: () -> Unit,
    onGroupSelected: (Group) -> Unit
) {
    val handleGroupSelection = { group: Group ->
        onGroupSelected(group)
    }

    var searchQuery by remember { mutableStateOf("") }
    val expandedCourses = remember { mutableStateMapOf<String, Boolean>() }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .padding(horizontal = 16.dp)
                .padding(top = topInset + 12.dp)
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = AppMotionScheme.defaultEffectsSpec()) +
                        expandVertically(animationSpec = AppMotionScheme.defaultSpatialSpec())
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = ExpressiveShapes.InnerCard,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.School,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Привет!",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Выбери свою группу, чтобы увидеть расписание. Это можно будет изменить позже в настройках.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Поиск группы...") },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        shape = ExpressiveShapes.SearchBar,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.weight(1f)) {
                OnboardingContent(
                    groupsUiState = groupsUiState,
                    searchQuery = searchQuery,
                    expandedCourses = expandedCourses,
                    onRetry = onRetry,
                    onGroupSelected = handleGroupSelection
                )
            }
        }
    }
}

@Composable
fun OnboardingContent(
    groupsUiState: GroupsUiState,
    searchQuery: String,
    expandedCourses: MutableMap<String, Boolean>,
    onRetry: () -> Unit,
    onGroupSelected: (Group) -> Unit
) {
    val haptic = rememberHapticFeedback()
    when (groupsUiState) {
        is GroupsUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ExpressiveLoadingIndicator(
                    modifier = Modifier.size(40.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        is GroupsUiState.Error -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Не удалось загрузить список",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = {
                    haptic.click()
                    onRetry()
                }) {
                    Text("Повторить")
                }
            }
        }
        is GroupsUiState.Success -> {
            var selectedLevelTab by remember { mutableStateOf(EducationLevel.BACHELOR) }

            Column(modifier = Modifier.fillMaxSize()) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    SegmentedButton(
                        selected = selectedLevelTab == EducationLevel.BACHELOR,
                        onClick = {
                            haptic.selection()
                            selectedLevelTab = EducationLevel.BACHELOR
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Бакалавриат", fontWeight = FontWeight.Bold)
                    }
                    SegmentedButton(
                        selected = selectedLevelTab == EducationLevel.MASTER,
                        onClick = {
                            haptic.selection()
                            selectedLevelTab = EducationLevel.MASTER
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Магистратура", fontWeight = FontWeight.Bold)
                    }
                }

                val filteredGroups = remember(groupsUiState.groups, selectedLevelTab, searchQuery) {
                    groupsUiState.groups
                        .filter { it.level == selectedLevelTab }
                        .filter { it.name.contains(searchQuery, ignoreCase = true) }
                }

                if (filteredGroups.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Ничего не найдено",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val groupedGroups = remember(filteredGroups) {
                        filteredGroups.groupBy { it.course }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        contentPadding = PaddingValues(
                            bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        )
                    ) {
                        groupedGroups.forEach { (course, groups) ->
                            val isExpanded = expandedCourses[course] ?: (searchQuery.isNotEmpty())

                            item(key = "course_$course", contentType = "course_header") {
                                Surface(
                                    onClick = {
                                        haptic.lightTick()
                                        expandedCourses[course] = !isExpanded
                                    },
                                    color = if (isExpanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = if (isExpanded) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                    shape = ExpressiveShapes.InnerCard,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = course,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                            contentDescription = null
                                        )
                                    }
                                }
                            }

                            if (isExpanded) {
                                itemsIndexed(
                                    items = groups,
                                    key = { _, group -> group.id },
                                    contentType = { _, _ -> "group" }
                                ) { index, group ->
                                    Surface(
                                        onClick = {
                                            haptic.success()
                                            onGroupSelected(group)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = ExpressiveShapes.segmentedItem(index, groups.size),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow
                                    ) {
                                        ListItem(
                                            headlineContent = {
                                                Text(
                                                    group.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            },
                                            leadingContent = {
                                                Icon(
                                                    Icons.Rounded.Group,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(22.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
