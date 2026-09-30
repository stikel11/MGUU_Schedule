package com.mguuschedule.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.GlanceTheme
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mguuschedule.model.Lesson
import com.mguuschedule.model.toLesson
import com.mguuschedule.repository.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

class NextLessonWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val nextLesson = withContext(Dispatchers.IO) {
            fetchNextLesson(context)
        }

        provideContent {
            WidgetContent(nextLesson)
        }
    }

    private suspend fun fetchNextLesson(context: Context): Lesson? {
        val db = AppDatabase.getDatabase(context)
        val todayStr = LocalDate.now().toString()
        val upcomingEntities = db.scheduleDao().getUpcomingLessons(todayStr)
        val lessons = upcomingEntities.mapNotNull { it.toLesson() }

        val today = LocalDate.now()
        val now = LocalTime.now()

        // Пытаемся найти ближайшую сегодняшнюю не закончишуюся пару, либо первую будущую
        return lessons.find { it.date.isEqual(today) && it.endTime.isAfter(now) }
            ?: lessons.find { it.date.isAfter(today) }
    }

    @Composable
    private fun WidgetContent(lesson: Lesson?) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start
        ) {
            if (lesson != null) {
                Text(
                    text = "Ближайшая пара",
                    style = TextStyle(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(
                    text = lesson.title,
                    style = TextStyle(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "${lesson.startTime} — ${lesson.endTime} • Ауд. ${lesson.room}"
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = lesson.type
                )
            } else {
                Text(
                    text = "Занятий нет",
                    style = TextStyle(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "Отдыхайте и набирайтесь сил 🎉"
                )
            }
        }
    }
}

class NextLessonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextLessonWidget()
}
