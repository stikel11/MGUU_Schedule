package com.mguuschedule.repository

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.mguuschedule.model.CampusCriteria
import com.mguuschedule.model.CampusReview
import com.mguuschedule.model.CampusTeacher
import com.mguuschedule.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

class TeacherRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("teacher_cache", Context.MODE_PRIVATE)
    private val gson = GsonBuilder().create()

    private fun getCacheKey(teacherName: String): String = "teacher_${normalizeName(teacherName).hashCode()}"
    private fun getCacheTimeKey(teacherName: String): String = "teacher_time_${normalizeName(teacherName).hashCode()}"

    suspend fun getCachedTeacherProfile(teacherName: String): CampusTeacher? = withContext(Dispatchers.IO) {
        val json = prefs.getString(getCacheKey(teacherName), null) ?: return@withContext null
        try {
            gson.fromJson(json, CampusTeacher::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun isCacheStale(teacherName: String): Boolean = withContext(Dispatchers.IO) {
        val lastUpdated = prefs.getLong(getCacheTimeKey(teacherName), 0L)
        val ttlMillis = 3 * 24 * 60 * 60 * 1000L // 3 дня
        System.currentTimeMillis() - lastUpdated > ttlMillis
    }

    private suspend fun saveTeacherToCache(teacherName: String, teacher: CampusTeacher) = withContext(Dispatchers.IO) {
        val json = gson.toJson(teacher)
        prefs.edit()
            .putString(getCacheKey(teacherName), json)
            .putLong(getCacheTimeKey(teacherName), System.currentTimeMillis())
            .apply()
    }

    suspend fun getTeacherProfile(teacherName: String): Result<CampusTeacher> = withContext(Dispatchers.IO) {
        try {
            val normalizedName = normalizeName(teacherName)
            val searchUrl = "https://campusapp.ru/api/next/teachers?search=${URLEncoder.encode(normalizedName, "UTF-8")}"
            
            AppLogger.d("TEACHER_PARSER", "Поиск преподавателя: $searchUrl")
            
            val searchResponse = Jsoup.connect(searchUrl).ignoreContentType(true).execute().body()
            val searchJson = JsonParser.parseString(searchResponse).asJsonObject
            val items = searchJson.getAsJsonArray("items")
            
            AppLogger.d("TEACHER_PARSER", "Найдено результатов поиска: ${items.size()}")

            var targetTeacherId: String? = null

            // Ищем точные совпадения по нормализованному ФИО (case-insensitive)
            val exactMatches = items.map { it.asJsonObject }.filter { obj ->
                val name = obj.get("name")?.asString ?: ""
                name.equals(normalizedName, ignoreCase = true)
            }

            if (exactMatches.isEmpty()) {
                return@withContext Result.failure(Exception("Преподаватель не найден на Campus"))
            }

            if (exactMatches.size == 1) {
                targetTeacherId = exactMatches[0].get("_id").asString
            } else {
                // Пытаемся найти среди точных совпадений преподавателя из МГУУ
                val mguuMatch = exactMatches.filter { obj ->
                    val orgName = obj.getAsJsonObject("organization")?.get("name")?.asString ?: ""
                    orgName.contains("МГУУ Правительства Москвы", ignoreCase = true) || 
                    orgName.contains("Университет Правительства Москвы", ignoreCase = true)
                }

                if (mguuMatch.size == 1) {
                    targetTeacherId = mguuMatch[0].get("_id").asString
                } else {
                    return@withContext Result.failure(Exception("Найдено несколько профилей с таким ФИО (неоднозначно)"))
                }
            }

            AppLogger.d("TEACHER_PARSER", "Выбран teacher ID: $targetTeacherId")

            // Шаг 2. Загружаем информацию о профиле преподавателя (JSON API)
            val profileApiUrl = "https://campusapp.ru/api/next/teachers/$targetTeacherId"
            val profileResponse = Jsoup.connect(profileApiUrl).ignoreContentType(true).execute().body()
            val profileJson = JsonParser.parseString(profileResponse).asJsonObject

            val bestMatchName = profileJson.get("name")?.asString ?: normalizedName
            val department = profileJson.getAsJsonObject("extra")?.get("department")?.asString ?: ""
            val university = profileJson.getAsJsonObject("organization")?.get("name")?.asString ?: ""
            
            var aggregateRating = 0f
            var reviewCount = 0
            val criteria = mutableListOf<CampusCriteria>()
            val tags = mutableListOf<String>()

            val ratingObj = profileJson.getAsJsonObject("rating")
            if (ratingObj != null && !ratingObj.isJsonNull) {
                aggregateRating = ratingObj.get("value")?.asFloat ?: 0f
                reviewCount = ratingObj.get("count")?.asInt ?: 0
                
                if (ratingObj.has("criteria") && !ratingObj.get("criteria").isJsonNull) {
                    val criteriaArr = ratingObj.getAsJsonArray("criteria")
                    criteriaArr?.forEach { c ->
                        val cObj = c.asJsonObject
                        criteria.add(CampusCriteria(cObj.get("title").asString, cObj.get("value").asFloat))
                    }
                }

                if (ratingObj.has("tags") && !ratingObj.get("tags").isJsonNull) {
                    val tagsArr = ratingObj.getAsJsonArray("tags")
                    tagsArr?.forEach { t ->
                        val tObj = t.asJsonObject
                        tags.add(tObj.get("title").asString)
                    }
                }
            }

            // Шаг 3. Загружаем HTML страницу с отзывами
            val reviewsUrl = "https://campusapp.ru/reviews/teachers/teacher-$targetTeacherId"
            AppLogger.d("TEACHER_PARSER", "URL страницы с отзывами: $reviewsUrl")

            val doc = Jsoup.connect(reviewsUrl).get()
            val reviewArticles = doc.select("article")
            AppLogger.d("TEACHER_PARSER", "Найдено DOM контейнеров отзывов: ${reviewArticles.size}")

            val reviews = mutableListOf<CampusReview>()

            for (article in reviewArticles) {
                try {
                    val divs = article.children().filter { it.tagName() == "div" }
                    val p = article.selectFirst("p")
                    
                    var author = "Студент"
                    var date = "Неизвестная дата"
                    var rating = 0f
                    
                    val headerDiv = divs.getOrNull(0)
                    if (headerDiv != null) {
                        val spans = headerDiv.children().filter { it.tagName() == "span" }
                        if (spans.size >= 2) {
                            val authorDateText = spans[0].text()
                            val parts = authorDateText.split("·", limit = 2).map { it.trim() }
                            if (parts.size == 2) {
                                author = parts[0]
                                date = parts[1]
                            } else {
                                author = authorDateText
                            }
                            
                            val ratingSpan = spans[1]
                            val ratingText = ratingSpan.selectFirst("span")?.text() ?: ratingSpan.text()
                            rating = ratingText.toFloatOrNull() ?: 0f
                        }
                    }
                    
                    val text = p?.text() ?: ""
                    
                    val reviewTags = mutableListOf<String>()
                    val tagsDiv = divs.getOrNull(1)
                    if (tagsDiv != null) {
                        tagsDiv.select("span").forEach { span ->
                            val tagText = span.text().trim()
                            if (tagText.isNotBlank()) reviewTags.add(tagText)
                        }
                    }
                    
                    if (text.isNotBlank() || reviewTags.isNotEmpty()) {
                        reviews.add(
                            CampusReview(
                                author = author,
                                date = date,
                                rating = rating,
                                text = text,
                                tags = reviewTags
                            )
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.e("TEACHER_PARSER", "Ошибка парсинга отдельного отзыва: ${e.message}")
                }
            }

            AppLogger.d("TEACHER_PARSER", "Успешно распарсено отзывов: ${reviews.size}")

            val teacherProfile = CampusTeacher(
                name = bestMatchName,
                university = university,
                department = department,
                rating = aggregateRating,
                reviewCount = reviewCount,
                criteria = criteria,
                tags = tags,
                reviews = reviews
            )
            
            saveTeacherToCache(teacherName, teacherProfile)
            Result.success(teacherProfile)

        } catch (e: Exception) {
            AppLogger.e("TEACHER_PARSER", "Ошибка загрузки профиля преподавателя: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun normalizeName(name: String): String {
        return name.replace("\u00A0", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
