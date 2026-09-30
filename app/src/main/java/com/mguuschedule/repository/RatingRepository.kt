package com.mguuschedule.repository

import android.util.Log
import com.mguuschedule.model.AcademicPeriod
import com.mguuschedule.model.ControlPoint
import com.mguuschedule.model.EduGroup
import com.mguuschedule.model.GroupRatingPage
import com.mguuschedule.model.StudentRating
import com.mguuschedule.model.SubjectScore
import com.mguuschedule.util.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.LocalDate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.roundToInt

/**
 * Репозиторий и парсер балльно-рейтинговой системы (БРС) МГУУ на основе Jsoup.
 */
class RatingRepository(
    private val ratingDao: RatingDao? = null
) {

    companion object {
        private const val TAG = "RatingRepository"
        private const val BASE_PORTAL_URL = "https://portal.mguu.ru/student/"
        private const val USER_AGENT = "Mozilla/5.0 (Android 14; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0"
        private const val TIMEOUT_MS = 10_000
    }

    /**
     * Загрузка кэша рейтинга из базы данных Room.
     */
    suspend fun getCachedRating(groupId: String, zachetka: String = "", yearId: String = "", semId: String = ""): RatingEntity? {
        return ratingDao?.getRatingCache(groupId, zachetka, yearId, semId)
            ?: ratingDao?.getLatestRatingCache()
    }

    /**
     * Сохранение кэша рейтинга в базу данных Room.
     */
    suspend fun saveCachedRating(entity: RatingEntity) {
        ratingDao?.saveRatingCache(entity)
    }

    /**
     * Генерация URL для запроса рейтинга группы с определенным учебным годом и семестром.
     */
    fun buildGroupUrl(groupId: String, groupName: String, yearId: String? = null, semId: String? = null): String {
        val encodedName = runCatching {
            URLEncoder.encode(groupName, "UTF-8")
        }.getOrDefault(groupName)

        val yearParam = if (!yearId.isNullOrBlank()) "&year=${yearId.trim()}" else ""
        val semParam = if (!semId.isNullOrBlank()) "&sem=${semId.trim()}" else ""

        return "${BASE_PORTAL_URL}rating.php?groupid=${groupId.trim()}&groupname=$encodedName$yearParam$semParam#rating"
    }

    /**
     * Парсинг главного каталога групп БРС.
     */
    suspend fun parseMasterGroupsCatalogue(url: String): List<EduGroup> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = fetchDocument(url)
            val groupLinks = doc.select("a.btn.btn-default.btn-group, a.btn-group")
            val groups = mutableListOf<EduGroup>()

            for (link in groupLinks) {
                val groupName = normalizeText(link.text())
                val groupUrl = resolveAbsoluteUrl(link, "href")

                if (groupName.isNotBlank() && groupUrl.isNotBlank()) {
                    groups.add(EduGroup(groupName = groupName, url = groupUrl))
                }
            }

            Log.d(TAG, "Успешно распарсено групп в каталоге: ${groups.size}")
            groups
        }.getOrElse { e ->
            Log.e(TAG, "Ошибка при парсинге каталога групп: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Парсинг страницы рейтинга группы (Уровень 1): список студентов + селекторы учебных годов и семестров.
     */
    suspend fun parseGroupList(url: String): GroupRatingPage = withContext(Dispatchers.IO) {
        runCatching {
            val doc = fetchDocument(url)

            // 1. Парсинг доступных учебных годов (select#year option)
            val years = mutableListOf<AcademicPeriod>()
            val yearOptions = doc.select("select#year option")
            for (option in yearOptions) {
                val id = option.attr("value").trim()
                val title = normalizeText(option.text())
                val isSelected = option.hasAttr("selected") || option.attr("selected") == "selected"
                if (id.isNotBlank() && title.isNotBlank()) {
                    years.add(AcademicPeriod(id = id, title = title, isSelected = isSelected))
                }
            }

            // 2. Парсинг доступных семестров (select#semestr option)
            val semesters = mutableListOf<AcademicPeriod>()
            val semOptions = doc.select("select#semestr option, select#sem option")
            for (option in semOptions) {
                val id = option.attr("value").trim()
                val title = normalizeText(option.text())
                val isSelected = option.hasAttr("selected") || option.attr("selected") == "selected"
                if (id.isNotBlank() && title.isNotBlank()) {
                    semesters.add(AcademicPeriod(id = id, title = title, isSelected = isSelected))
                }
            }

            // 3. Парсинг списка студентов
            val rows = doc.select("div.brs div.ct-action")
            val students = mutableListOf<StudentRating>()

            for ((index, row) in rows.withIndex()) {
                // Пропускаем заголовок (первая строка)
                if (index == 0) continue

                val link = row.selectFirst("a[href]") ?: continue
                val zachetkaText = normalizeText(link.selectFirst("h5.brs-fio")?.text() ?: link.text())

                if (zachetkaText.isBlank() || isHeaderOrSummaryRow(zachetkaText)) continue

                val detailUrl = resolveAbsoluteUrl(link, "href")
                val module1Text = row.selectFirst("h5.brs-data1, .brs-data1")?.text()
                val module2Text = row.selectFirst("h5.brs-data2, .brs-data2")?.text()
                val totalScoreText = row.selectFirst("h5.brs-rating, .brs-rating")?.text()

                val module1 = parseNullableFloat(module1Text)
                val module2 = parseNullableFloat(module2Text)
                val totalScore = parseNullableFloat(totalScoreText)

                students.add(
                    StudentRating(
                        zachetka = zachetkaText,
                        module1 = module1,
                        module2 = module2,
                        totalScore = totalScore,
                        detailUrl = detailUrl
                    )
                )
            }

            Log.d(TAG, "Успешно распарсено: студентов = ${students.size}, годов = ${years.size}, семестров = ${semesters.size}")
            GroupRatingPage(
                students = students,
                availableYears = years,
                availableSemesters = semesters
            )
        }.getOrElse { e ->
            Log.e(TAG, "Ошибка при парсинге рейтинга группы: ${e.message}", e)
            GroupRatingPage(
                students = emptyList(),
                availableYears = emptyList(),
                availableSemesters = emptyList()
            )
        }
    }

    /**
     * Парсинг персонального рейтинга дисциплин студента (Уровень 2).
     */
    suspend fun parseStudentSubjects(url: String): List<SubjectScore> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = fetchDocument(url)
            val rows = doc.select("div.brs div.ct-action")
            val subjects = mutableListOf<SubjectScore>()

            for ((index, row) in rows.withIndex()) {
                val rowText = normalizeText(row.text())

                // Пропускаем заголовок (первая строка) и итоговые строки ("Сводный рейтинг")
                if (index == 0 || isHeaderOrSummaryRow(rowText)) continue

                val link = row.selectFirst("a[href]") ?: continue
                val rawSubjectText = normalizeText(link.selectFirst("h5.brs-fio")?.text() ?: link.text())

                if (rawSubjectText.isBlank() || isHeaderOrSummaryRow(rawSubjectText)) continue

                val (title, controlType) = splitSubjectAndControlType(rawSubjectText)
                if (title.isBlank() || isHeaderOrSummaryRow(title)) continue

                val detailUrl = resolveAbsoluteUrl(link, "href")
                val module1Text = row.selectFirst("h5.brs-data1, .brs-data1")?.text()
                val module2Text = row.selectFirst("h5.brs-data2, .brs-data2")?.text()
                val totalScoreText = row.selectFirst("h5.brs-rating, .brs-rating")?.text()

                val module1 = parseNullableFloat(module1Text)
                val module2 = parseNullableFloat(module2Text)
                val totalScore = parseNullableFloat(totalScoreText)

                subjects.add(
                    SubjectScore(
                        title = title,
                        controlType = controlType,
                        module1 = module1,
                        module2 = module2,
                        totalScore = totalScore,
                        detailUrl = detailUrl
                    )
                )
            }

            Log.d(TAG, "Успешно распарсено дисциплин: ${subjects.size}")
            subjects.sortedWith(compareBy<SubjectScore> { it.title }.thenBy { it.controlType })
        }.getOrElse { e ->
            Log.e(TAG, "Ошибка при парсинге предметов студента: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Парсинг контрольных точек для конкретной дисциплины (Уровень 3).
     * Парсит исключительно мобильную версию таблицы: div#ratingmobile table tr.
     */
    suspend fun parseControlPoints(url: String): List<ControlPoint> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = fetchDocument(url)
            val rows = doc.select("div#ratingmobile table tr, div#ratingmobile tr")
            val points = mutableListOf<ControlPoint>()

            for (row in rows) {
                // Пропускаем строки с заголовками <th>
                if (row.select("th").isNotEmpty()) continue

                val tds = row.select("td")
                if (tds.size < 3) continue

                val pointNameText = normalizeText(
                    tds[0].selectFirst("h5.brs-point-new")?.text() ?: tds[0].text()
                )
                val dateText = normalizeText(
                    tds[1].selectFirst("h5.brs-point-new")?.text() ?: tds[1].text()
                )
                val scoreText = tds[2].selectFirst("h5.brs-point-new")?.text() ?: tds[2].text()

                // Фильтруем строки, если дата равна "—" / пуста, или имя содержит "Модуль"/"Итого"
                if (dateText == "—" || dateText.isBlank()) continue
                if (isHeaderOrSummaryRow(pointNameText) || isHeaderOrSummaryRow(dateText)) continue

                val score = parseNullableInt(scoreText)

                points.add(
                    ControlPoint(
                        pointName = if (pointNameText.isNotBlank()) pointNameText else "КТ ${points.size + 1}",
                        date = dateText,
                        score = score
                    )
                )
            }

            Log.d(TAG, "Успешно распарсено контрольных точек: ${points.size}")
            val currentYear = LocalDate.now().year
            points.sortedWith(compareBy<ControlPoint> { cp ->
                SearchEngine.parseControlPointDate(cp.date, currentYear) ?: LocalDate.MAX
            }.thenBy { it.pointName })
        }.getOrElse { e ->
            Log.e(TAG, "Ошибка при парсинге контрольных точек: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Разделение названия дисциплины и формы контроля по последней запятой.
     */
    private fun splitSubjectAndControlType(rawText: String): Pair<String, String> {
        val normalized = normalizeText(rawText)
        val lastComma = normalized.lastIndexOf(',')

        return if (lastComma != -1 && lastComma < normalized.length - 1) {
            val title = normalized.substring(0, lastComma).trim()
            val controlType = normalized.substring(lastComma + 1).trim()
            title to controlType
        } else {
            normalized to ""
        }
    }

    /**
     * Проверка, является ли текст заголовком или итоговой/служебной строкой.
     */
    private fun isHeaderOrSummaryRow(text: String): Boolean {
        val lower = normalizeText(text).lowercase()
        return lower.contains("сводный рейтинг") ||
                lower == "итого" ||
                lower.contains("модуль 1") ||
                lower.contains("модуль 2") ||
                lower.contains("модуль") ||
                lower == "всего" ||
                lower.contains("фио") ||
                lower.contains("зачетка") ||
                lower.contains("студент")
    }

    /**
     * Формирование абсолютного URL.
     */
    private fun resolveAbsoluteUrl(element: Element, attributeKey: String = "href"): String {
        val absUrl = element.absUrl(attributeKey)
        if (absUrl.isNotBlank()) return absUrl

        val rawHref = element.attr(attributeKey).trim()
        return when {
            rawHref.startsWith("http://") || rawHref.startsWith("https://") -> rawHref
            rawHref.startsWith("/") -> "https://portal.mguu.ru$rawHref"
            else -> "$BASE_PORTAL_URL$rawHref"
        }
    }

    /**
     * Нормализация текста: очистка от \u00A0, сжатие пробелов, trim.
     */
    private fun normalizeText(text: String): String {
        return text.replace("\u00A0", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Безопасный парсинг дробного числа из строки (замена запятой на точку).
     */
    private fun parseNullableFloat(text: String?): Float? {
        if (text.isNullOrBlank()) return null
        val clean = normalizeText(text).replace(",", ".")
        if (clean == "—" || clean == "-" || clean == "null") return null

        return clean.toFloatOrNull()
    }

    /**
     * Безопасный парсинг целого числа из строки.
     */
    private fun parseNullableInt(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val clean = normalizeText(text).replace(",", ".")
        if (clean == "—" || clean == "-" || clean == "null") return null

        return clean.toIntOrNull() ?: clean.toFloatOrNull()?.roundToInt()
    }

    /**
     * Менеджер SSL-сокетов для работы с порталом МГУУ.
     */
    private fun createSslSocketFactory() = try {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("SSL").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        sslContext.socketFactory
    } catch (e: Exception) {
        null
    }

    /**
     * Загрузка HTML-документа с помощью Jsoup.
     */
    private fun fetchDocument(url: String): Document {
        val connection = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .referrer("https://portal.mguu.ru/")
            .header("Accept-Language", "ru-RU,ru;q=0.9")
            .timeout(TIMEOUT_MS)
            .followRedirects(true)

        createSslSocketFactory()?.let {
            connection.sslSocketFactory(it)
        }

        return connection.get()
    }
}
