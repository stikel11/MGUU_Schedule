package com.mguuschedule.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mguuschedule.model.Group
import com.mguuschedule.ui.theme.AppMotionScheme
import com.mguuschedule.util.rememberHapticFeedback

@Composable
fun OnboardingScreen(
    groupsUiState: GroupsUiState,
    onRetry: () -> Unit,
    onGroupSelected: (Group) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val expandedCourses = remember { mutableStateMapOf<String, Boolean>() }
    
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.statusBarsPadding())
            
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = AppMotionScheme.defaultEffectsSpec()) +
                        expandVertically(animationSpec = AppMotionScheme.defaultSpatialSpec())
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(top = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Привет!",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Выбери свою группу, чтобы увидеть расписание. Это можно будет изменить позже в профиле.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Поиск группы...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
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
                    onGroupSelected = onGroupSelected
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
                CircularProgressIndicator()
            }
        }
        is GroupsUiState.Error -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.ErrorOutline, 
                    contentDescription = null, 
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Не удалось загрузить список", 
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
            val filteredGroups = remember(groupsUiState.groups, searchQuery) {
                groupsUiState.groups.filter {
                    it.name.contains(searchQuery, ignoreCase = true)
                }
            }

            if (filteredGroups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Ничего не найдено", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                val groupedGroups = remember(filteredGroups) {
                    filteredGroups.groupBy { it.course }
                }
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    groupedGroups.forEach { (course, groups) ->
                        val isExpanded = expandedCourses[course] ?: (searchQuery.isNotEmpty())
                        
                        item(key = course) {
                            Surface(
                                onClick = {
                                    haptic.lightTick()
                                    expandedCourses[course] = !isExpanded
                                },
                                color = if (isExpanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
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
                                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                }
                            }
                        }

                        if (isExpanded) {
                            items(groups, key = { it.id }) { group ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            haptic.success()
                                            onGroupSelected(group)
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    )
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
                                                Icons.Default.Group, 
                                                contentDescription = null, 
                                                modifier = Modifier.size(24.dp),
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
