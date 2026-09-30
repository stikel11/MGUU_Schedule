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
            val searchUrl = "https://campusapp.ru/api/next/teachers?search=${URLEncoder.encode(normalizedName, "UTF-8")}&city=5f3ac415d91f50788747aa97&limit=20&offset=0"
            
            val jsonResponse = Jsoup.connect(searchUrl).ignoreContentType(true).execute().body()
            val jsonObject = JsonParser.parseString(jsonResponse).asJsonObject
            val items = jsonObject.getAsJsonArray("items")
            
            var targetTeacherId: String? = null
            var bestMatchName = ""

            // Собираем точные совпадения по ФИО
            val exactMatches = items.map { it.asJsonObject }.filter { obj ->
                val name = obj.get("name")?.asString ?: ""
                val lastNameSearch = normalizedName.substringBefore(" ").trim()
                val lastNameResult = name.substringBefore(" ").trim()
                // Более точное совпадение можно сделать, но пока проверяем по нормализованному ФИО (как минимум фамилия)
                // Для надежности берем совпадение фамилии
                lastNameSearch.equals(lastNameResult, ignoreCase = true)
            }

            if (exactMatches.isEmpty()) {
                return@withContext Result.failure(Exception("Преподаватель не найден на Campus"))
            }

            if (exactMatches.size == 1) {
                // Если найдено ровно одно точное совпадение, берем его независимо от организации
                val match = exactMatches[0]
                targetTeacherId = match.get("_id").asString
                bestMatchName = match.get("name").asString
            } else {
                // Если найдено несколько совпадений, ищем того, кто из МГУУ
                val mguuMatch = exactMatches.find { obj ->
                    val orgName = obj.getAsJsonObject("organization")?.get("name")?.asString ?: ""
                    orgName.contains("МГУУ Правительства Москвы", ignoreCase = true) || 
                    orgName.contains("Правительства Москвы", ignoreCase = true)
                }

                if (mguuMatch != null) {
                    targetTeacherId = mguuMatch.get("_id").asString
                    bestMatchName = mguuMatch.get("name").asString
                } else {
                    // Если никто не из МГУУ, и их несколько, то неоднозначность
                    return@withContext Result.failure(Exception("Найдено несколько профилей с таким ФИО (неоднозначно)"))
                }
            }
            
            // Загружаем профиль преподавателя
            val profileUrl = "https://campusapp.ru/reviews/teachers/teacher-$targetTeacherId"
            val doc = Jsoup.connect(profileUrl).get()
            
            var aggregateRating = 0f
            var reviewCount = 0
            
            // Парсинг JSON-LD (самый надежный способ извлечь общую информацию)
            val scriptTags = doc.select("script[type=application/ld+json]")
            for (script in scriptTags) {
                try {
                    val ldJson = JsonParser.parseString(script.html()).asJsonObject
                    if (ldJson.has("aggregateRating")) {
                        val agg = ldJson.getAsJsonObject("aggregateRating")
                        aggregateRating = agg.get("ratingValue")?.asFloat ?: 0f
                        reviewCount = agg.get("reviewCount")?.asInt ?: 0
                    }
                } catch (e: Exception) {
                    AppLogger.e("TEACHER_PARSER", "Ошибка парсинга JSON-LD: ${e.message}")
                }
            }
            
            // Парсинг HTML
            var department = ""
            try {
                // Поиск по текстовым признакам: обычно кафедра идет после имени или университета
                val pTags = doc.select("p, span, div")
                val deptTag = pTags.find { it.text().contains("Кафедра", ignoreCase = true) }
                if (deptTag != null) {
                    department = deptTag.text().trim()
                }
            } catch (e: Exception) {}

            val criteria = mutableListOf<CampusCriteria>()
            val criteriaNames = listOf(
                "Компетентность", 
                "Умение донести материал", 
                "Справедливость оценивания", 
                "Актуальность материала"
            )
            
            criteriaNames.forEach { critName ->
                try {
                    // Ищем элемент с названием критерия
                    val critElems = doc.select("*:containsOwn($critName)")
                    val critElem = critElems.lastOrNull()
                    if (critElem != null) {
                        // Значение обычно лежит рядом в том же родительском контейнере
                        val parentText = critElem.parent()?.text() ?: ""
                        val scoreMatch = Regex("(\\d[.,]\\d)").find(parentText)
                        if (scoreMatch != null) {
                            val score = scoreMatch.value.replace(",", ".").toFloat()
                            criteria.add(CampusCriteria(critName, score))
                        }
                    }
                } catch (e: Exception) {}
            }
            
            val tags = mutableListOf<String>()
            val reviews = mutableListOf<CampusReview>()
            
            // Попытка извлечь отзывы из HTML 
            // Отзывы на Campus обычно находятся внутри div или article
            // Ориентируемся на наличие текста, даты и оценки
            try {
                val reviewBlocks = doc.select("div:has(span:matchesOwn(\\d{1,2}\\s[а-яА-Я]+\\s\\d{4})):has(div:matchesOwn(\\d[.,]\\d))")
                // Это очень приблизительный селектор, если он не сработает - ничего страшного
                for (block in reviewBlocks.take(10)) { // берем первые 10
                    val textContent = block.text()
                    // Здесь можно было бы сделать более детальный разбор, 
                    // но без жесткой привязки к классам это сложно.
                    // Для надежности берем базовые данные.
                    val ratingMatch = Regex("(\\d[.,]\\d)").find(textContent)
                    val rating = ratingMatch?.value?.replace(",", ".")?.toFloatOrNull() ?: 0f
                    
                    val dateMatch = Regex("(\\d{1,2}\\s[а-яА-Я]+\\s\\d{4})").find(textContent)
                    val date = dateMatch?.value ?: "Неизвестная дата"
                    
                    // Текст отзыва (пытаемся исключить служебные слова)
                    var text = textContent
                    
                    reviews.add(
                        CampusReview(
                            author = "Студент", // Campus часто не отдает имена
                            date = date,
                            rating = rating,
                            text = text,
                            tags = emptyList()
                        )
                    )
                }
            } catch (e: Exception) {}

            val teacherProfile = CampusTeacher(
                name = bestMatchName,
                university = "МГУУ Правительства Москвы",
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
        return name.replace("\u00A0", " ").trim()
    }
}
