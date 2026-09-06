package com.mguuschedule.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mguuschedule.repository.WeatherData
import com.mguuschedule.ui.theme.AppMotionScheme
import com.mguuschedule.util.SemanticHapticFeedback
import com.mguuschedule.util.rememberHapticFeedback
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekCalendar(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    weatherData: WeatherData? = null,
    unreadNotificationCount: Int = 0,
    onNotificationHistoryClick: () -> Unit = {},
    onShareDayClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMonthPicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val locale = remember { Locale("ru") }
    
    val baseMonday = remember {
        val now = LocalDate.now()
        now.minusDays(now.dayOfWeek.value.toLong() - 1)
    }
    
    val totalWeeks = 10000
    val centerPage = totalWeeks / 2
    
    val initialPage = remember {
        val selectedMonday = selectedDate.minusDays(selectedDate.dayOfWeek.value.toLong() - 1)
        val weekDiff = ChronoUnit.WEEKS.between(baseMonday, selectedMonday).toInt()
        centerPage + weekDiff
    }
    
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { totalWeeks }
    )
    val haptic = rememberHapticFeedback()

    val visibleMonday = remember(pagerState.currentPage) {
        baseMonday.plusWeeks((pagerState.currentPage - centerPage).toLong())
    }
    
    val selectedMondayOfDate = remember(selectedDate) {
        selectedDate.minusDays(selectedDate.dayOfWeek.value.toLong() - 1)
    }
    
    val displayDate = remember(visibleMonday, selectedMondayOfDate, selectedDate) {
        if (visibleMonday.isEqual(selectedMondayOfDate)) selectedDate else visibleMonday
    }

    val displayMonthName = remember(displayDate, locale) {
        displayDate.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            .replaceFirstChar { it.uppercase() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp)
    ) {
        // Top Row: Month Picker Button, Notification Bell, Share, and Weather
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Month Selector Button - M3 Expressive Pill Badge
            Surface(
                onClick = { 
                    haptic.lightTick()
                    showMonthPicker = true 
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.height(40.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayMonthName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ArrowDropDown, 
                        contentDescription = null, 
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Right Action Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Notification Bell with Unread Badge
                Surface(
                    onClick = {
                        haptic.lightTick()
                        onNotificationHistoryClick()
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (unreadNotificationCount > 0) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                            contentDescription = "История уведомлений",
                            tint = if (unreadNotificationCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        if (unreadNotificationCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 8.dp, end = 8.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }

                // Share Day Schedule Poster Button
                Surface(
                    onClick = {
                        haptic.click()
                        onShareDayClick()
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Поделиться расписанием дня",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                weatherData?.let { data ->
                    WeatherChip(data = data)
                }
            }
        }

        if (showMonthPicker) {
            ModalBottomSheet(
                onDismissRequest = { showMonthPicker = false },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 36.dp, start = 20.dp, end = 20.dp)
                ) {
                    Text(
                        text = "Выберите месяц",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                    val months = remember(locale) {
                        Month.entries.map { month ->
                            month to month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
                                .replaceFirstChar { it.uppercase() }
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(months, key = { it.first.name }) { (month, name) ->
                            val isSelected = displayDate.month == month
                            Surface(
                                onClick = {
                                    haptic.selection()
                                    val newDate = LocalDate.of(selectedDate.year, month.value, 1)
                                    onDateSelected(newDate)
                                    showMonthPicker = false
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.height(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            key = { it }
        ) { page ->
            val weekMonday = remember(page) { baseMonday.plusWeeks((page - centerPage).toLong()) }
            val days = remember(weekMonday) { (0..6).map { weekMonday.plusDays(it.toLong()) } }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                days.forEach { date ->
                    val isSameMonth = date.month == displayDate.month
                    DayItem(
                        date = date,
                        isSelected = date.isEqual(selectedDate),
                        isSameMonth = isSameMonth,
                        onClick = { 
                            haptic.selection()
                            onDateSelected(date) 
                        },
                        modifier = Modifier.weight(1f),
                        locale = locale
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
    }
    
    LaunchedEffect(selectedDate) {
        val targetMonday = selectedDate.minusDays(selectedDate.dayOfWeek.value.toLong() - 1)
        val weekDiff = ChronoUnit.WEEKS.between(baseMonday, targetMonday).toInt()
        val targetPage = centerPage + weekDiff
        
        if (targetPage in 0 until totalWeeks && pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    val settledPage by remember { derivedStateOf { pagerState.settledPage } }
    LaunchedEffect(settledPage) {
        val weekMonday = baseMonday.plusWeeks((settledPage - centerPage).toLong())
        val currentSelectedMonday = selectedDate.minusDays(selectedDate.dayOfWeek.value.toLong() - 1)
        
        if (!weekMonday.isEqual(currentSelectedMonday)) {
            onDateSelected(weekMonday)
        }
    }
}

@Composable
fun WeatherChip(data: WeatherData) {
    val icon = remember(data.weatherCode) {
        when (data.weatherCode) {
            0 -> Icons.Default.WbSunny
            in 1..3 -> Icons.Default.Cloud
            in 51..67, in 80..82 -> Icons.Default.WaterDrop
            in 71..77 -> Icons.Default.AcUnit
            in 95..99 -> Icons.Default.FlashOn
            else -> Icons.Default.WbCloudy
        }
    }
    
    val tempText = remember(data.temperature) {
        val sign = if (data.temperature > 0) "+" else ""
        "$sign${data.temperature.toInt()}°"
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.height(40.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = tempText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun DayItem(
    date: LocalDate,
    isSelected: Boolean,
    isSameMonth: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale("ru")
) {
    val dayName = remember(date, locale) {
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            .replaceFirstChar { it.uppercase() }
    }
    val dayNumber = remember(date) { date.dayOfMonth.toString() }

    val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    
    val targetTextColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isWeekend -> MaterialTheme.colorScheme.error.copy(alpha = if (isSameMonth) 1f else 0.38f)
        !isSameMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    val targetLabelColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
        isWeekend -> MaterialTheme.colorScheme.error.copy(alpha = if (isSameMonth) 0.75f else 0.38f)
        !isSameMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Effects Motion for color transitions (Standard M3 Effects)
    val textColor by animateColorAsState(
        targetValue = targetTextColor,
        animationSpec = AppMotionScheme.fastEffectsSpec(),
        label = "textColor"
    )
    val labelColor by animateColorAsState(
        targetValue = targetLabelColor,
        animationSpec = AppMotionScheme.fastEffectsSpec(),
        label = "labelColor"
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
        animationSpec = AppMotionScheme.fastEffectsSpec(),
        label = "bgColor"
    )
    
    // Spatial Motion for scale transition
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 0.96f,
        animationSpec = AppMotionScheme.fastSpatialSpec(),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(76.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(22.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Text(
            text = dayName, 
            style = MaterialTheme.typography.labelMedium, 
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = labelColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = dayNumber, 
            style = MaterialTheme.typography.titleLarge, 
            fontWeight = FontWeight.ExtraBold, 
            color = textColor
        )
    }
}
