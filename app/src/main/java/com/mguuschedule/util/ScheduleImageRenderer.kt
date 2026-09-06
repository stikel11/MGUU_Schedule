package com.mguuschedule.util

import android.R
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.mguuschedule.model.Lesson
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class PosterPalette(
    val bgColor: Int,
    val surfaceColor: Int,
    val primaryColor: Int,
    val primaryContainerColor: Int,
    val onPrimaryContainerColor: Int,
    val textColor: Int,
    val textVariantColor: Int
)

object ScheduleImageRenderer {

    fun shareDaySchedule(
        context: Context,
        date: LocalDate,
        lessons: List<Lesson>,
        isDarkTheme: Boolean = false,
        shareStyle: Int = 0
    ) {
        val bitmap = renderDayScheduleBitmap(context, date, lessons, isDarkTheme, shareStyle)
        val file = File(context.cacheDir, "shared_schedule.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Поделиться расписанием на день")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun resolvePalette(context: Context, shareStyle: Int, isDarkTheme: Boolean): PosterPalette {
        return when (shareStyle) {
            1 -> PosterPalette( // Dark
                bgColor = Color.parseColor("#111318"),
                surfaceColor = Color.parseColor("#1D2024"),
                primaryColor = Color.parseColor("#AEC6FF"),
                primaryContainerColor = Color.parseColor("#004397"),
                onPrimaryContainerColor = Color.parseColor("#D8E2FF"),
                textColor = Color.parseColor("#E1E2E8"),
                textVariantColor = Color.parseColor("#C4C6D0")
            )
            2 -> PosterPalette( // Light
                bgColor = Color.parseColor("#F8F9FE"),
                surfaceColor = Color.parseColor("#FFFFFF"),
                primaryColor = Color.parseColor("#1A56B0"),
                primaryContainerColor = Color.parseColor("#D8E2FF"),
                onPrimaryContainerColor = Color.parseColor("#001A43"),
                textColor = Color.parseColor("#191C20"),
                textVariantColor = Color.parseColor("#43474E")
            )
            3 -> PosterPalette( // Monochrome B&W
                bgColor = Color.parseColor("#FFFFFF"),
                surfaceColor = Color.parseColor("#F2F3F7"),
                primaryColor = Color.parseColor("#000000"),
                primaryContainerColor = Color.parseColor("#E0E2E8"),
                onPrimaryContainerColor = Color.parseColor("#000000"),
                textColor = Color.parseColor("#000000"),
                textVariantColor = Color.parseColor("#555555")
            )
            else -> { // 0: Dynamic Material You
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        val sysAccent = context.getColor(R.color.system_accent1_500)
                        val sysAccentLight = context.getColor(R.color.system_accent1_100)
                        val sysAccentDark = context.getColor(R.color.system_accent1_900)
                        val sysNeutralBg = if (isDarkTheme) Color.parseColor("#111318") else Color.parseColor("#F8F9FE")
                        val sysSurface = if (isDarkTheme) Color.parseColor("#1D2024") else Color.parseColor("#FFFFFF")

                        PosterPalette(
                            bgColor = sysNeutralBg,
                            surfaceColor = sysSurface,
                            primaryColor = if (isDarkTheme) sysAccentLight else sysAccent,
                            primaryContainerColor = if (isDarkTheme) sysAccentDark else sysAccentLight,
                            onPrimaryContainerColor = if (isDarkTheme) sysAccentLight else sysAccentDark,
                            textColor = if (isDarkTheme) Color.parseColor("#E1E2E8") else Color.parseColor("#191C20"),
                            textVariantColor = if (isDarkTheme) Color.parseColor("#C4C6D0") else Color.parseColor("#43474E")
                        )
                    } catch (e: Exception) {
                        resolvePalette(context, if (isDarkTheme) 1 else 2, isDarkTheme)
                    }
                } else {
                    resolvePalette(context, if (isDarkTheme) 1 else 2, isDarkTheme)
                }
            }
        }
    }

    fun renderDayScheduleBitmap(
        context: Context,
        date: LocalDate,
        lessons: List<Lesson>,
        isDarkTheme: Boolean,
        shareStyle: Int = 0
    ): Bitmap {
        val width = 1080
        val padding = 54
        
        val palette = resolvePalette(context, shareStyle, isDarkTheme)

        val titlePaint = TextPaint().apply {
            color = palette.textColor
            textSize = 52f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val dateSubtitlePaint = TextPaint().apply {
            color = palette.primaryColor
            textSize = 38f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val timePaint = TextPaint().apply {
            color = palette.textColor
            textSize = 36f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val timeEndPaint = TextPaint().apply {
            color = palette.textVariantColor
            textSize = 32f
            isAntiAlias = true
        }

        val lessonTitlePaint = TextPaint().apply {
            color = palette.textColor
            textSize = 42f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val badgePaint = TextPaint().apply {
            color = palette.onPrimaryContainerColor
            textSize = 30f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val teacherPaint = TextPaint().apply {
            color = palette.textVariantColor
            textSize = 34f
            isAntiAlias = true
        }

        val roomPaint = TextPaint().apply {
            color = palette.primaryColor
            textSize = 34f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val footerPaint = TextPaint().apply {
            color = palette.textVariantColor
            textSize = 28f
            isAntiAlias = true
        }

        // Calculate card layout heights dynamically
        val cardWidth = width - (padding * 2) - 180 // Leave 180px for left time column
        val cardInnerWidth = cardWidth - 64

        var totalHeight = padding + 180 // Header space
        
        if (lessons.isEmpty()) {
            totalHeight += 300
        } else {
            lessons.forEach { lesson ->
                val titleLayout = StaticLayout.Builder.obtain(lesson.title, 0, lesson.title.length, lessonTitlePaint, cardInnerWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.15f)
                    .build()
                
                val cardHeight = 120 + titleLayout.height + 80
                totalHeight += cardHeight + 36
            }
        }
        totalHeight += 120 // Footer space

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.bgColor)

        val bgPaint = Paint().apply { isAntiAlias = true }

        // 1. Draw Header
        val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("ru"))
            .replaceFirstChar { it.uppercase() }
        val dateStr = "$dayName, ${date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru")))}"

        // App Pill Badge
        bgPaint.color = palette.primaryContainerColor
        val badgeRect = RectF(padding.toFloat(), padding.toFloat(), padding + 340f, padding + 56f)
        canvas.drawRoundRect(badgeRect, 28f, 28f, bgPaint)
        
        canvas.drawText("MGUUSchedule", padding + 28f, padding + 38f, badgePaint)

        canvas.drawText("Расписание занятий", padding.toFloat(), padding + 120f, titlePaint)
        canvas.drawText(dateStr, padding.toFloat(), padding + 170f, dateSubtitlePaint)

        var currentY = padding + 230f

        // 2. Draw Lessons
        if (lessons.isEmpty()) {
            bgPaint.color = palette.surfaceColor
            val emptyRect = RectF(padding.toFloat(), currentY, (width - padding).toFloat(), currentY + 220f)
            canvas.drawRoundRect(emptyRect, 40f, 40f, bgPaint)
            
            canvas.drawText("Пар нет • Можно отдыхать!", padding + 40f, currentY + 120f, lessonTitlePaint)
        } else {
            lessons.forEach { lesson ->
                val timeX = padding.toFloat()
                val cardX = padding + 180f

                // Left Time Column
                canvas.drawText(lesson.startTime.toString(), timeX, currentY + 60f, timePaint)
                canvas.drawText(lesson.endTime.toString(), timeX, currentY + 105f, timeEndPaint)

                val titleLayout = StaticLayout.Builder.obtain(lesson.title, 0, lesson.title.length, lessonTitlePaint, cardInnerWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.15f)
                    .build()

                val cardHeight = 110f + titleLayout.height + 70f
                
                // Draw Card Surface
                bgPaint.color = palette.surfaceColor
                val cardRect = RectF(cardX, currentY, (width - padding).toFloat(), currentY + cardHeight)
                canvas.drawRoundRect(cardRect, 40f, 40f, bgPaint)

                var cardY = currentY + 44f

                // Number & Type Badge
                bgPaint.color = palette.primaryContainerColor
                val badgeText = "${lesson.number} пара • ${lesson.type}"
                val badgeWidth = badgePaint.measureText(badgeText) + 36f
                val pillRect = RectF(cardX + 32f, cardY - 26f, cardX + 32f + badgeWidth, cardY + 18f)
                canvas.drawRoundRect(pillRect, 22f, 22f, bgPaint)
                canvas.drawText(badgeText, cardX + 50f, cardY + 6f, badgePaint)

                cardY += 50f

                // Subject Title
                canvas.save()
                canvas.translate(cardX + 32f, cardY)
                titleLayout.draw(canvas)
                canvas.restore()

                cardY += titleLayout.height + 40f

                // Teacher & Room
                val teacherText = if (lesson.teacher.isNotBlank()) lesson.teacher else "Преподаватель не указан"
                canvas.drawText(teacherText, cardX + 32f, cardY, teacherPaint)

                val roomText = if (lesson.room.isNotBlank()) formatClassroom(lesson.room) else "—"
                val roomWidth = roomPaint.measureText(roomText)
                canvas.drawText(roomText, (width - padding - 32) - roomWidth, cardY, roomPaint)

                currentY += cardHeight + 28f
            }
        }

        // 3. Draw Footer
        val footerY = totalHeight - 40f
        canvas.drawText("Университет МГУУ Правительства Москвы • mguu.ru", padding.toFloat(), footerY, footerPaint)

        return bitmap
    }
}
