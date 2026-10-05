package com.mguuschedule.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mguuschedule.model.ControlPoint
import com.mguuschedule.model.StudentRating
import com.mguuschedule.model.SubjectScore
import com.mguuschedule.repository.AppDatabase
import com.mguuschedule.repository.RatingEntity
import com.mguuschedule.repository.RatingRepository
import com.mguuschedule.util.AppLogger
import com.mguuschedule.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class RatingUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val repository = RatingRepository()
    private val database = AppDatabase.getDatabase(context)
    private val ratingDao = database.ratingDao()
    private val gson = Gson()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val groupId = prefs.getString("selected_group_id", "000000283") ?: "000000283"
        val groupName = prefs.getString("selected_group_name", "25М-УГКП21") ?: "25М-УГКП21"
        val savedZachetka = prefs.getString("selected_zachetka", "") ?: ""

        AppLogger.d("RATING_WORKER", "Старт фоновой глубокой проверки БРС для группы $groupId ($groupName)")

        return@withContext try {
            val groupUrl = repository.buildGroupUrl(groupId, groupName)
            val ratingPage = repository.parseGroupList(groupUrl)
            val students = ratingPage.students

            if (students.isEmpty()) {
                AppLogger.w("RATING_WORKER", "Получен пустой список студентов при проверке БРС")
                return@withContext Result.success()
            }

            val selectedStudent = if (savedZachetka.isNotBlank()) {
                students.find { it.zachetka.equals(savedZachetka, ignoreCase = true) } ?: students.first()
            } else {
                students.first()
            }

            val newSubjects = repository.parseStudentSubjects(selectedStudent.detailUrl)

            // Сбор детальных контрольных точек для каждого предмета
            val newControlPointsMap = mutableMapOf<String, List<ControlPoint>>()
            for (subject in newSubjects) {
                if (subject.detailUrl.isNotBlank()) {
                    runCatching {
                        val points = repository.parseControlPoints(subject.detailUrl)
                        if (points.isNotEmpty()) {
                            newControlPointsMap[subject.detailUrl] = points
                        }
                    }
                }
            }

            // Получаем старый кэш из базы Room
            val oldCache = ratingDao.getRatingCache(groupId, selectedStudent.zachetka, "", "")

            val oldStudent = oldCache?.studentJson?.let {
                runCatching { gson.fromJson(it, StudentRating::class.java) }.getOrNull()
            }

            val oldSubjectsType = object : TypeToken<List<SubjectScore>>() {}.type
            val oldSubjects: List<SubjectScore> = oldCache?.subjectsJson?.let {
                runCatching { gson.fromJson<List<SubjectScore>>(it, oldSubjectsType) }.getOrNull()
            } ?: emptyList()

            val oldPointsMapType = object : TypeToken<Map<String, List<ControlPoint>>>() {}.type
            val oldControlPointsMap: Map<String, List<ControlPoint>> = oldCache?.controlPointsJson?.let {
                runCatching { gson.fromJson<Map<String, List<ControlPoint>>>(it, oldPointsMapType) }.getOrNull()
            } ?: emptyMap()

            var notificationId = 2000

            fun formatScore(score: Float?): String {
                if (score == null) return "—"
                return if (score % 1.0f == 0.0f) {
                    score.toInt().toString()
                } else {
                    score.toString().replace('.', ',')
                }
            }

            // Детальная сверка старых данных с новыми
            if (oldSubjects.isNotEmpty()) {
                for (subject in newSubjects) {
                    val oldSubject = oldSubjects.find { it.title.equals(subject.title, ignoreCase = true) }

                    if (oldSubject == null) {
                        // Появился новый предмет
                        if (subject.totalScore != null) {
                            val scoreStr = formatScore(subject.totalScore)
                            NotificationHelper.showNotification(
                                applicationContext,
                                NotificationHelper.CHANNEL_SCHEDULE_CHANGES,
                                "📊 Новый предмет в БРС",
                                "Добавлен предмет \"${subject.title}\": $scoreStr б.",
                                notificationId++
                            )
                        }
                    } else {
                        // 1. Проверка изменения баллов за Модуль 1
                        if (oldSubject.module1 != subject.module1 && subject.module1 != null) {
                            val m1Str = formatScore(subject.module1)
                            NotificationHelper.showNotification(
                                applicationContext,
                                NotificationHelper.CHANNEL_SCHEDULE_CHANGES,
                                "📊 Баллы за Модуль 1",
                                "По предмету \"${subject.title}\" выставлены баллы за Модуль 1: $m1Str б.",
                                notificationId++
                            )
                        }

                        // 2. Проверка изменения баллов за Модуль 2
                        if (oldSubject.module2 != subject.module2 && subject.module2 != null) {
                            val m2Str = formatScore(subject.module2)
                            NotificationHelper.showNotification(
                                applicationContext,
                                NotificationHelper.CHANNEL_SCHEDULE_CHANGES,
                                "📊 Баллы за Модуль 2",
                                "По предмету \"${subject.title}\" выставлены баллы за Модуль 2: $m2Str б.",
                                notificationId++
                            )
                        }

                        // 3. Детальная сверка контрольных точек (КТ)
                        val oldPoints = oldControlPointsMap[subject.detailUrl] ?: emptyList()
                        val newPoints = newControlPointsMap[subject.detailUrl] ?: emptyList()

                        for (newPoint in newPoints) {
                            val oldPoint = oldPoints.find { it.pointName.equals(newPoint.pointName, ignoreCase = true) }

                            if (oldPoint == null && newPoint.score != null) {
                                NotificationHelper.showNotification(
                                    applicationContext,
                                    NotificationHelper.CHANNEL_SCHEDULE_CHANGES,
                                    "📊 Выставлены баллы за КТ",
                                    "По предмету \"${subject.title}\" выставлены баллы за ${newPoint.pointName}: ${newPoint.score} б.",
                                    notificationId++
                                )
                            } else if (oldPoint != null && oldPoint.score != newPoint.score && newPoint.score != null) {
                                NotificationHelper.showNotification(
                                    applicationContext,
                                    NotificationHelper.CHANNEL_SCHEDULE_CHANGES,
                                    "📊 Изменен балл за КТ",
                                    "По предмету \"${subject.title}\" балл за ${newPoint.pointName} изменен на ${newPoint.score} б.",
                                    notificationId++
                                )
                            }
                        }
                    }
                }
            } else if (oldStudent != null && selectedStudent.totalScore != null && oldStudent.totalScore != selectedStudent.totalScore) {
                // Если нет деталей предметов, но общий балл изменился
                val oldScoreStr = formatScore(oldStudent.totalScore)
                val newScoreStr = formatScore(selectedStudent.totalScore)
                NotificationHelper.showNotification(
                    applicationContext,
                    NotificationHelper.CHANNEL_SCHEDULE_CHANGES,
                    "📊 Обновление рейтинга БРС",
                    "Ваш итоговый балл изменился с $oldScoreStr на $newScoreStr",
                    notificationId++
                )
            }

            val activeYearId = ratingPage.availableYears.find { it.isSelected }?.id ?: ""
            val activeSemId = ratingPage.availableSemesters.find { it.isSelected }?.id ?: ""

            // Сохраняем свежий кэш с контрольными точками в базу Room
            val newCache = RatingEntity(
                groupId = groupId,
                zachetka = selectedStudent.zachetka,
                yearId = activeYearId,
                semId = activeSemId,
                studentJson = gson.toJson(selectedStudent),
                subjectsJson = gson.toJson(newSubjects),
                studentsListJson = gson.toJson(students),
                controlPointsJson = gson.toJson(newControlPointsMap),
                updatedAt = System.currentTimeMillis()
            )
            ratingDao.saveRatingCache(newCache)

            AppLogger.d("RATING_WORKER", "Глубокая фоновая синхронизация БРС завершена")
            Result.success()
        } catch (e: Exception) {
            AppLogger.e("RATING_WORKER", "Ошибка при фоновом обновлении БРС: ${e.message}", e)
            Result.retry()
        }
    }
}
