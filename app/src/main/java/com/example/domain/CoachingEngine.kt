package com.example.domain

import com.example.model.DailyRecommendation
import com.example.model.DayRhythmIndicator
import com.example.model.DumbbellItem
import com.example.model.SportType
import com.example.model.SuggestionOption
import com.example.model.TodayStatus
import com.example.model.WeeklyRhythmSummary
import com.example.model.WorkoutInsights
import com.example.model.WorkoutSession

object CoachingEngine {

    private const val BADGE_IDEAL = "Ritme Utama"
    private const val BADGE_LIGHT = "Anti-Mager"
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    private const val HOURS_48_MILLIS = 48L * 60L * 60L * 1000L

    private val lowerBodyKeywords = listOf("squat", "lunge", "deadlift")

    /**
     * Analyzes workout history into structured [WorkoutInsights] before generating recommendations.
     */
    fun analyzeInsights(
        sessions: List<WorkoutSession>,
        nowMillis: Long = System.currentTimeMillis()
    ): WorkoutInsights {
        if (sessions.isEmpty()) return WorkoutInsights()

        val sortedActive = sessions
            .filter { it.sportType.isActiveMovement }
            .mapNotNull { session ->
                val millis = DateTimeHelper.parseIsoToMillis(session.startedAtIso) ?: return@mapNotNull null
                session to millis
            }
            .sortedByDescending { it.second }

        if (sortedActive.isEmpty()) return WorkoutInsights()

        val (newestSession, newestMillis) = sortedActive.first()
        val daysSinceLastWorkout = DateTimeHelper.calendarDaysBetween(newestMillis, nowMillis)
            .coerceAtLeast(0)

        // Consecutive active days without rest (counting backwards from today, or from yesterday if today not yet logged)
        val activeDateKeys = sortedActive.map { DateTimeHelper.toLocalDateKey(it.second) }.toSet()
        val todayStart = DateTimeHelper.startOfLocalDayMillis(nowMillis)
        val todayKey = DateTimeHelper.toLocalDateKey(todayStart)
        val yesterdayKey = DateTimeHelper.toLocalDateKey(todayStart - MILLIS_PER_DAY)

        var consecutiveActiveDays = 0
        val startOffset = when {
            activeDateKeys.contains(todayKey) -> 0
            activeDateKeys.contains(yesterdayKey) -> 1
            else -> -1
        }
        if (startOffset >= 0) {
            var offset = startOffset
            while (activeDateKeys.contains(DateTimeHelper.toLocalDateKey(todayStart - offset * MILLIS_PER_DAY))) {
                consecutiveActiveDays++
                offset++
            }
        }

        // Sessions within the last 48 hours
        val last48hSessions = sortedActive.filter { (_, millis) ->
            val diff = nowMillis - millis
            diff in 0L..HOURS_48_MILLIS
        }
        val lastSessionIn48h = last48hSessions.firstOrNull()?.first

        val lowerBodyFatigueSource = last48hSessions.firstOrNull { (session, _) ->
            isLowerBodyTaxingSession(session)
        }?.first

        // Last 7 days counts (dayDiff 0..6)
        var weeklyCardioCount = 0
        var weeklyStrengthCount = 0
        var hasRunningThisWeek = false

        for ((session, millis) in sortedActive) {
            val dayDiff = DateTimeHelper.calendarDaysBetween(millis, nowMillis)
            if (dayDiff in 0..6) {
                if (session.sportType.isCardio) weeklyCardioCount++
                if (session.sportType.isStrength) weeklyStrengthCount++
                if (session.sportType == SportType.RUNNING) hasRunningThisWeek = true
            }
        }

        // Ready for progression if the last 2 DUMBBELL sessions both have RPE <= 5
        val recentStrengthSessions = sortedActive
            .map { it.first }
            .filter { it.sportType == SportType.DUMBBELL }
            .take(2)
        val readyForProgression = recentStrengthSessions.size == 2 &&
            recentStrengthSessions.all { it.rpe <= 5 }

        return WorkoutInsights(
            daysSinceLastWorkout = daysSinceLastWorkout,
            consecutiveActiveDays = consecutiveActiveDays,
            lastSession = lastSessionIn48h,
            isLowerBodyFatigued = lowerBodyFatigueSource != null,
            lowerBodyFatigueSource = lowerBodyFatigueSource,
            weeklyCardioCount = weeklyCardioCount,
            weeklyStrengthCount = weeklyStrengthCount,
            hasRunningThisWeek = hasRunningThisWeek,
            readyForProgression = readyForProgression
        )
    }

    fun generateDailyRecommendation(
        sessions: List<WorkoutSession>,
        todayStatus: TodayStatus,
        nowMillis: Long = System.currentTimeMillis()
    ): DailyRecommendation {
        val insights = analyzeInsights(sessions, nowMillis)
        return when (todayStatus) {
            TodayStatus.FRESH -> buildFreshRecommendation(sessions, insights, nowMillis)
            TodayStatus.TIRED -> buildTiredRecommendation(sessions, insights, nowMillis)
            TodayStatus.BUSY -> buildBusyRecommendation(sessions, insights, nowMillis)
        }
    }

    fun computeWeeklyRhythm(
        sessions: List<WorkoutSession>,
        nowMillis: Long = System.currentTimeMillis()
    ): WeeklyRhythmSummary {
        val dailyMinutesByKey = mutableMapOf<String, Int>()
        val dailyStrengthByKey = mutableMapOf<String, Int>()
        val dailyCardioByKey = mutableMapOf<String, Int>()
        val dailyCountByKey = mutableMapOf<String, Int>()

        val last7DayActiveDateKeys = mutableSetOf<String>()
        var totalActiveMinutes = 0
        var strengthCount = 0
        var cardioCount = 0

        for (session in sessions) {
            if (!session.sportType.isActiveMovement) continue
            val millis = DateTimeHelper.parseIsoToMillis(session.startedAtIso) ?: continue
            val dateKey = DateTimeHelper.toLocalDateKey(millis)
            val safeMin = session.durationMin.coerceAtLeast(0)

            dailyMinutesByKey[dateKey] = (dailyMinutesByKey[dateKey] ?: 0) + safeMin
            dailyCountByKey[dateKey] = (dailyCountByKey[dateKey] ?: 0) + 1
            if (session.sportType.isStrength) {
                dailyStrengthByKey[dateKey] = (dailyStrengthByKey[dateKey] ?: 0) + safeMin
            }
            if (session.sportType.isCardio) {
                dailyCardioByKey[dateKey] = (dailyCardioByKey[dateKey] ?: 0) + safeMin
            }

            val dayDiff = DateTimeHelper.calendarDaysBetween(millis, nowMillis)
            if (dayDiff in 0..6) {
                last7DayActiveDateKeys.add(dateKey)
                totalActiveMinutes += safeMin
                if (session.sportType.isStrength) strengthCount++
                if (session.sportType.isCardio) cardioCount++
            }
        }

        fun buildIndicators(slots: List<WeekDaySlot>): List<DayRhythmIndicator> = slots.map { slot ->
            val dayTotal = dailyMinutesByKey[slot.dateKey] ?: 0
            val dayStrength = dailyStrengthByKey[slot.dateKey] ?: 0
            val dayCardio = dailyCardioByKey[slot.dateKey] ?: 0
            val dayCount = dailyCountByKey[slot.dateKey] ?: 0
            DayRhythmIndicator(
                dayLabel = slot.dayLabel,
                dateKey = slot.dateKey,
                isActive = dayCount > 0,
                isToday = slot.isToday,
                isFuture = slot.isFuture,
                totalMinutes = dayTotal,
                strengthMinutes = dayStrength,
                cardioMinutes = dayCardio,
                sessionCount = dayCount,
                shortDateLabel = slot.shortDateLabel
            )
        }

        val weekIndicators = buildIndicators(DateTimeHelper.currentWeekMonToSun(nowMillis))
        val rollingIndicators = buildIndicators(DateTimeHelper.rollingLast7Days(nowMillis))

        return WeeklyRhythmSummary(
            activeDaysCount = last7DayActiveDateKeys.size,
            totalActiveMinutes = totalActiveMinutes,
            strengthSessionCount = strengthCount,
            cardioSessionCount = cardioCount,
            weekDays = weekIndicators,
            last7Days = rollingIndicators
        )
    }

    // -------------------------------------------------------------------------
    // 1. FRESH ("Senggang & Fit")
    // -------------------------------------------------------------------------
    private fun buildFreshRecommendation(
        sessions: List<WorkoutSession>,
        insights: WorkoutInsights,
        nowMillis: Long
    ): DailyRecommendation {
        // 1a. Empty history
        if (sessions.isEmpty() || insights.daysSinceLastWorkout == null) {
            return DailyRecommendation(
                idealOption = SuggestionOption(
                    badge = BADGE_IDEAL,
                    title = "Bodyweight Dasar / Lari Santai",
                    sportType = SportType.DUMBBELL,
                    estimatedDurationMin = 20,
                    durationLabel = "20 menit",
                    reason = "Riwayat latihanmu masih kosong — hari pertama paling pas dibuka dengan gerak dasar 20 menit yang ramah di badan.",
                    relaxedGuidance = "Pilih salah satu: sirkuit Bodyweight Dasar santai di rumah atau joging pelan dengan aturan talk test (tetap bisa mengobrol nyaman).",
                    defaultRpe = 4,
                    suggestedDumbbellItems = listOf(
                        DumbbellItem("Push-up (atau Knee Push-up)", 0.0, 3, 8),
                        DumbbellItem("Bodyweight Squat", 0.0, 3, 12),
                        DumbbellItem("Glute Bridge", 0.0, 3, 12),
                        DumbbellItem("Plank", 0.0, 3, 30)
                    )
                ),
                lightOption = SuggestionOption(
                    badge = BADGE_LIGHT,
                    title = "Jalan Santai Pembuka",
                    sportType = SportType.WALKING,
                    estimatedDurationMin = 15,
                    durationLabel = "15 menit",
                    reason = "Belum ada sesi tercatat sebelumnya. Jalan santai 15 menit adalah pintu masuk paling bebas stres untuk mulai rutin.",
                    relaxedGuidance = "Pasang sepatu, jalan keliling lingkungan sekitar tanpa target kecepatan sambil menikmati udara segar.",
                    defaultRpe = 2
                ),
                contextSummary = "Hari pertama mulai — pilih yang paling terasa ramah untukmu.",
                insightBadge = "🌱 Langkah pertama: mulai dari ritme nyaman tanpa target berat",
                insights = insights
            )
        }

        // 1b. Consecutive active days >= 4 -> Active Recovery
        if (insights.consecutiveActiveDays >= 4) {
            val streak = insights.consecutiveActiveDays
            return DailyRecommendation(
                idealOption = SuggestionOption(
                    badge = BADGE_IDEAL,
                    title = "Pemulihan Aktif / Jalan Santai",
                    sportType = SportType.WALKING,
                    estimatedDurationMin = 22,
                    durationLabel = "20–25 menit",
                    reason = "Kamu sudah aktif $streak hari berturut-turut tanpa jeda! Ingat, otot justru tumbuh dan menguat saat diberi ruang pemulihan.",
                    relaxedGuidance = "Jalan santai dengan napas super rileks selama 20–25 menit lalu tutup dengan peregangan lembut paha, pinggul, dan bahu.",
                    defaultRpe = 2
                ),
                lightOption = SuggestionOption(
                    badge = BADGE_LIGHT,
                    title = "Istirahat Total & Pulih Penuh",
                    sportType = SportType.REST,
                    estimatedDurationMin = 15,
                    durationLabel = "Hari Istirahat",
                    reason = "Setelah $streak hari konsisten bergerak, mengambil hari istirahat penuh adalah keputusan latihan yang cerdas.",
                    relaxedGuidance = "Fokus hidrasi, tidur cukup, dan peregangan ringan bila terasa kaku — besok badan akan kembali segar.",
                    defaultRpe = 1
                ),
                contextSummary = "Konsistensi luar biasa ($streak hari beruntun) — saatnya jaga pemulihan.",
                insightBadge = "🌿 Sudah $streak hari aktif beruntun — otot tumbuh saat istirahat",
                insights = insights
            )
        }

        // 1c. Lower body fatigued in last 48h -> Upper & Core
        if (insights.isLowerBodyFatigued) {
            val source = insights.lowerBodyFatigueSource
            val sourceDesc = describeSessionForReason(source, nowMillis)
            val shortSource = describeSessionShort(source, nowMillis)
            val progressionHint = if (insights.readyForProgression) {
                " Karena 2 sesi beban terakhirmu terasa ringan (RPE ≤ 5), coba tambah +1–2 repetisi atau naikkan beban sedikit."
            } else {
                ""
            }

            return DailyRecommendation(
                idealOption = SuggestionOption(
                    badge = BADGE_IDEAL,
                    title = "Upper & Core (Dumbbell / Bodyweight)",
                    sportType = SportType.DUMBBELL,
                    estimatedDurationMin = 25,
                    durationLabel = "20–30 menit",
                    reason = "Kaki masih butuh waktu pulih setelah sesi $sourceDesc. Fokus tubuh atas & core hari ini agar tetap aktif tanpa membebani kaki.",
                    relaxedGuidance = "Fokus kontrol tempo pelan untuk dada, punggung, bahu, dan perut tanpa tekanan di lutut.$progressionHint",
                    defaultRpe = 5,
                    suggestedDumbbellItems = listOf(
                        DumbbellItem("Dumbbell Floor Press", 5.0, 3, 10),
                        DumbbellItem("Bent-Over Row", 5.0, 3, 10),
                        DumbbellItem("Overhead Press", 4.0, 3, 10),
                        DumbbellItem("Plank", 0.0, 3, 30)
                    )
                ),
                lightOption = SuggestionOption(
                    badge = BADGE_LIGHT,
                    title = "Peregangan & Core Ringan",
                    sportType = SportType.DUMBBELL,
                    estimatedDurationMin = 12,
                    durationLabel = "10–15 menit",
                    reason = "Sambil membiarkan kaki pulih dari $shortSource, core lembut di matras membantu menjaga stabilitas tanpa rasa lelah.",
                    relaxedGuidance = "Lakukan 2 set santai Bird-Dog dan Plank pendek sambil diselingi peregangan betis serta paha belakang.",
                    defaultRpe = 3,
                    suggestedDumbbellItems = listOf(
                        DumbbellItem("Bird-Dog", 0.0, 2, 10),
                        DumbbellItem("Plank", 0.0, 2, 25)
                    )
                ),
                contextSummary = "Mengistirahatkan kaki sambil tetap menjaga ritme tubuh atas.",
                insightBadge = "💡 Kaki butuh pulih dari $shortSource",
                insights = insights
            )
        }

        // 1d. Strength lagging (< 2) while Cardio >= 2 in last 7 days
        if (insights.weeklyStrengthCount < 2 && insights.weeklyCardioCount >= 2) {
            val strengthText = if (insights.weeklyStrengthCount == 0) {
                "belum ada latihan beban"
            } else {
                "baru ${insights.weeklyStrengthCount} sesi beban"
            }
            val progressionGuidance = if (insights.readyForProgression) {
                " 2 sesi beban terakhirmu tercatat ringan (RPE ≤ 5) — tubuhmu sudah beradaptasi, coba naikkan beban 1–2 kg atau tambah 2 repetisi per set!"
            } else {
                " Pilih beban yang terasa nyaman dengan 2 repetisi tersisa di akhir set."
            }
            val badge = if (insights.readyForProgression) {
                "🔥 2 sesi beban terakhir terasa ringan, siap naik level!"
            } else if (insights.weeklyStrengthCount == 0) {
                "💡 Minggu ini belum ada latihan beban (${insights.weeklyCardioCount} sesi kardio)"
            } else {
                "💡 Kardio sudah ${insights.weeklyCardioCount} sesi — lengkapi sesi beban minggu ini"
            }

            return DailyRecommendation(
                idealOption = SuggestionOption(
                    badge = BADGE_IDEAL,
                    title = "Full Body Dumbbell / Bodyweight",
                    sportType = SportType.DUMBBELL,
                    estimatedDurationMin = 28,
                    durationLabel = "25–30 menit",
                    reason = "Dalam 7 hari terakhir kamu sudah ${insights.weeklyCardioCount} sesi kardio, tapi $strengthText. Penguatan otot menjaga sendi dan postur tetap prima.",
                    relaxedGuidance = "Lakukan 4 gerakan seimbang dengan istirahat 60 detik antar set.$progressionGuidance",
                    defaultRpe = 5,
                    suggestedDumbbellItems = listOf(
                        DumbbellItem("Goblet Squat", 5.0, 3, 10),
                        DumbbellItem("Dumbbell Row", 5.0, 3, 10),
                        DumbbellItem("Dumbbell Floor Press", 5.0, 3, 10),
                        DumbbellItem("Bicep Curl", 4.0, 3, 10)
                    )
                ),
                lightOption = SuggestionOption(
                    badge = BADGE_LIGHT,
                    title = "Bodyweight Dasar Ringkas",
                    sportType = SportType.DUMBBELL,
                    estimatedDurationMin = 14,
                    durationLabel = "12–15 menit",
                    reason = "Tetap mengisi porsi kekuatan minggu ini ($strengthText) tanpa perlu menyiapkan alat apa pun.",
                    relaxedGuidance = "Cukup 2–3 set santai di rumah. Fokus gerakan rapi dan napas teratur.",
                    defaultRpe = 4,
                    suggestedDumbbellItems = listOf(
                        DumbbellItem("Push-up (atau Knee Push-up)", 0.0, 2, 8),
                        DumbbellItem("Bodyweight Squat", 0.0, 2, 12),
                        DumbbellItem("Glute Bridge", 0.0, 2, 12),
                        DumbbellItem("Plank", 0.0, 2, 25)
                    )
                ),
                contextSummary = "Menyeimbangkan sesi kardio minggu ini dengan latihan kekuatan.",
                insightBadge = badge,
                insights = insights
            )
        }

        // 1e. No cardio or no running this week -> Easy Zone 2 Run
        if (insights.weeklyCardioCount == 0 || !insights.hasRunningThisWeek) {
            val cardioDetail = if (insights.weeklyCardioCount == 0) {
                "belum ada sesi kardio sama sekali dalam 7 hari terakhir (beban: ${insights.weeklyStrengthCount} sesi)"
            } else {
                "belum ada sesi lari santai minggu ini (${insights.weeklyCardioCount} kardio lain, ${insights.weeklyStrengthCount} beban)"
            }
            val badge = if (insights.weeklyCardioCount == 0) {
                "💡 Minggu ini belum ada sesi kardio — cocok untuk lari santai"
            } else {
                "💡 Belum ada lari Zona 2 minggu ini — fokus talk test tanpa target pace"
            }

            return DailyRecommendation(
                idealOption = SuggestionOption(
                    badge = BADGE_IDEAL,
                    title = "Lari Santai Zona 2 (Talk Test)",
                    sportType = SportType.RUNNING,
                    estimatedDurationMin = 30,
                    durationLabel = "25–35 menit",
                    reason = "Kamu $cardioDetail. Lari Zona 2 membangun fondasi jantung dengan santai tanpa mengejar pace.",
                    relaxedGuidance = "Gunakan talk test: jaga kecepatan cukup pelan hingga masih nyaman mengucapkan 1 kalimat utuh. Boleh selipkan jalan 1–2 menit kapan pun perlu.",
                    defaultRpe = 4
                ),
                lightOption = SuggestionOption(
                    badge = BADGE_LIGHT,
                    title = "Jalan Cepat Santai",
                    sportType = SportType.WALKING,
                    estimatedDurationMin = 18,
                    durationLabel = "15–20 menit",
                    reason = "Jika belum ingin berlari hari ini, jalan cepat 15–20 menit tetap melengkapi kebutuhan kardio mingguanmu.",
                    relaxedGuidance = "Ayunkan lengan secara natural dan jaga langkah mantap tapi tetap rileks tanpa terengah.",
                    defaultRpe = 3
                ),
                contextSummary = "Mengisi porsi aerobik santai minggu ini tanpa tekanan pace.",
                insightBadge = badge,
                insights = insights
            )
        }

        // 1f. Balanced default
        val preferStrengthToday = insights.lastSession?.sportType?.isCardio == true
        val progressionGuidance = if (insights.readyForProgression) {
            " Bonus progres: 2 sesi beban terakhirmu terasa ringan (RPE ≤ 5), saatnya naikkan beban atau 1–2 repetisi perlahan!"
        } else {
            ""
        }
        val badge = if (insights.readyForProgression) {
            "🔥 2 sesi beban terakhir terasa ringan, siap naik level!"
        } else {
            "✨ Ritme 7 hari seimbang (${insights.weeklyCardioCount} kardio • ${insights.weeklyStrengthCount} beban)"
        }

        val ideal = if (preferStrengthToday) {
            SuggestionOption(
                badge = BADGE_IDEAL,
                title = "Full Body Santai (Dumbbell / Bodyweight)",
                sportType = SportType.DUMBBELL,
                estimatedDurationMin = 30,
                durationLabel = "30 menit",
                reason = "Ritme 7 harimu sudah seimbang (${insights.weeklyCardioCount} kardio & ${insights.weeklyStrengthCount} beban). Karena sesi terakhir kardio, hari ini cocok untuk Full Body santai.",
                relaxedGuidance = "Nikmati 3 set gerakan seimbang dengan tempo terkontrol dan istirahat santai antar set.$progressionGuidance",
                defaultRpe = 5,
                suggestedDumbbellItems = listOf(
                    DumbbellItem("Goblet Squat", 5.0, 3, 10),
                    DumbbellItem("Dumbbell Row", 5.0, 3, 10),
                    DumbbellItem("Dumbbell Floor Press", 5.0, 3, 10),
                    DumbbellItem("Bicep Curl", 4.0, 3, 10)
                )
            )
        } else {
            SuggestionOption(
                badge = BADGE_IDEAL,
                title = "Lari Santai / Badminton / Full Body",
                sportType = SportType.RUNNING,
                estimatedDurationMin = 30,
                durationLabel = "30 menit",
                reason = "Ritme 7 harimu sudah sangat seimbang (${insights.weeklyCardioCount} kardio & ${insights.weeklyStrengthCount} beban). Pilih sesi 30 menit yang paling kamu nikmati hari ini!",
                relaxedGuidance = "Lari santai dengan talk test, main badminton rileks, atau ganti dengan Full Body santai di rumah — semua mendukung ritmemu.",
                defaultRpe = 5
            )
        }

        return DailyRecommendation(
            idealOption = ideal,
            lightOption = SuggestionOption(
                badge = BADGE_LIGHT,
                title = "Jalan Santai 15 Menit",
                sportType = SportType.WALKING,
                estimatedDurationMin = 15,
                durationLabel = "15 menit",
                reason = "Dengan ${insights.weeklyCardioCount + insights.weeklyStrengthCount} sesi aktif minggu ini, jalan santai 15 menit sudah cukup menjaga momentum tanpa lelah.",
                relaxedGuidance = "Berjalan santai menikmati suasana luar sambil merilekskan pikiran dan bahu.",
                defaultRpe = 2
            ),
            contextSummary = "Ritme mingguanmu sudah seimbang — teruskan dengan santai.",
            insightBadge = badge,
            insights = insights
        )
    }

    // -------------------------------------------------------------------------
    // 2. TIRED ("Agak Capek")
    // -------------------------------------------------------------------------
    private fun buildTiredRecommendation(
        sessions: List<WorkoutSession>,
        insights: WorkoutInsights,
        nowMillis: Long
    ): DailyRecommendation {
        val last48h = insights.lastSession

        // 2a. Muscle fatigue after heavy session (RPE >= 7 in last 48h)
        if (last48h != null && last48h.rpe >= 7) {
            val sessionDesc = describeSessionForReason(last48h, nowMillis)
            val shortDesc = describeSessionShort(last48h, nowMillis)
            return DailyRecommendation(
                idealOption = SuggestionOption(
                    badge = BADGE_IDEAL,
                    title = "Peregangan Dinamis & Jalan Ringan",
                    sportType = SportType.WALKING,
                    estimatedDurationMin = 15,
                    durationLabel = "15 menit",
                    reason = "Badan terasa capek wajar setelah sesi berat $sessionDesc. Jalan pelan dan peregangan dinamis bantu mempercepat pemulihan otot.",
                    relaxedGuidance = "Jalan super pelan 10 menit ditambah peregangan lembut pinggul, betis, dan punggung 5 menit. Jangan paksa intensitas.",
                    defaultRpe = 2
                ),
                lightOption = SuggestionOption(
                    badge = BADGE_LIGHT,
                    title = "Istirahat Total & Relaksasi",
                    sportType = SportType.REST,
                    estimatedDurationMin = 10,
                    durationLabel = "Bebas Rasa Bersalah",
                    reason = "Ototmu baru saja bekerja keras saat $shortDesc (RPE ${last48h.rpe}). Istirahat total hari ini justru investasi terbaik untuk sesi berikutnya.",
                    relaxedGuidance = "Minum cukup air, mandi air hangat, tarik napas dalam beberapa menit, dan tidurlah lebih awal tanpa rasa bersalah.",
                    defaultRpe = 1
                ),
                contextSummary = "Prioritas utama hari ini adalah pemulihan otot setelah sesi berat.",
                insightBadge = "💡 Otot butuh pulih dari $shortDesc (RPE ${last48h.rpe})",
                insights = insights
            )
        }

        // 2b. Lethargy from 2+ days of inactivity
        val gap = insights.daysSinceLastWorkout
        if (gap != null && gap >= 2) {
            return DailyRecommendation(
                idealOption = SuggestionOption(
                    badge = BADGE_IDEAL,
                    title = "Jalan Santai Segar",
                    sportType = SportType.WALKING,
                    estimatedDurationMin = 18,
                    durationLabel = "15–20 menit",
                    reason = "Sudah $gap hari tidak bergerak — rasa capek lesu sering kali mereda saat sirkulasi darah kembali mengalir lewat jalan santai.",
                    relaxedGuidance = "Keluar rumah sebentar, jalan pelan tanpa target langkah sambil hirup udara segar. Biasanya badan terasa lebih enteng di menit ke-7.",
                    defaultRpe = 2
                ),
                lightOption = SuggestionOption(
                    badge = BADGE_LIGHT,
                    title = "Peregangan Ringan di Kamar",
                    sportType = SportType.REST,
                    estimatedDurationMin = 10,
                    durationLabel = "10 menit",
                    reason = "Setelah $gap hari jeda dan energi sedang rendah, peregangan santai di kamar tetap membantu melepas kekakuan badan.",
                    relaxedGuidance = "Putar bahu, regangkan leher, cat-cow ringan, dan ambil napas perlahan di atas matras atau di samping tempat tidur.",
                    defaultRpe = 1
                ),
                contextSummary = "Gerak super lembut untuk mengusir rasa lesu setelah $gap hari jeda.",
                insightBadge = "💡 Sudah $gap hari tidak gerak — jalan ringan bantu lancarkan sirkulasi",
                insights = insights
            )
        }

        // 2c. Other tired state (first day or normal mild fatigue)
        val historyContext = if (sessions.isEmpty()) {
            "Walau hari pertama badan sedang agak capek, kamu tetap bisa mulai dengan versi paling lembut."
        } else if (insights.isLowerBodyFatigued) {
            val shortSrc = describeSessionShort(insights.lowerBodyFatigueSource, nowMillis)
            "Kaki masih agak lelah pasca $shortSrc, jadi cukup gerak atas/core super santai atau jalan pelan."
        } else {
            "Minggu ini kamu sudah mencatat ${insights.weeklyCardioCount} kardio & ${insights.weeklyStrengthCount} beban. Saat badan agak capek, gerak ringan 15 menit sudah sangat cukup."
        }

        val badge = if (insights.isLowerBodyFatigued) {
            "💡 Kaki butuh pulih dari ${describeSessionShort(insights.lowerBodyFatigueSource, nowMillis)}"
        } else if (sessions.isEmpty()) {
            "🌿 Lagi agak capek di hari pertama — mulai pelan saja tanpa tekanan"
        } else {
            "🌿 Minggu ini ${insights.weeklyCardioCount + insights.weeklyStrengthCount} sesi — hari ini utamakan yang lembut"
        }

        val ideal = if (insights.isLowerBodyFatigued) {
            SuggestionOption(
                badge = BADGE_IDEAL,
                title = "Upper/Core Santai di Matras",
                sportType = SportType.DUMBBELL,
                estimatedDurationMin = 15,
                durationLabel = "15 menit",
                reason = historyContext,
                relaxedGuidance = "Lakukan 2 set lembut Bird-Dog, Glute Bridge ringan, dan peregangan bahu. Berhenti kapan saja kalau napas terasa berat.",
                defaultRpe = 3,
                suggestedDumbbellItems = listOf(
                    DumbbellItem("Bird-Dog", 0.0, 2, 10),
                    DumbbellItem("Plank", 0.0, 2, 20)
                )
            )
        } else {
            SuggestionOption(
                badge = BADGE_IDEAL,
                title = "Upper/Core Santai atau Jalan Pelan",
                sportType = SportType.WALKING,
                estimatedDurationMin = 15,
                durationLabel = "15 menit",
                reason = historyContext,
                relaxedGuidance = "Jalan pelan 15 menit sambil melemaskan bahu, atau ganti dengan 2 set peregangan core ringan di rumah.",
                defaultRpe = 2
            )
        }

        return DailyRecommendation(
            idealOption = ideal,
            lightOption = SuggestionOption(
                badge = BADGE_LIGHT,
                title = "Istirahat / Napas Santai",
                sportType = SportType.REST,
                estimatedDurationMin = 10,
                durationLabel = "10 menit",
                reason = "Mendengarkan sinyal lelah tubuh adalah kunci agar kebiasaan olahraga bertahan jangka panjang.",
                relaxedGuidance = "Duduk atau berbaring nyaman, lakukan napas dalam 4 detik tarik – 6 detik buang selama beberapa menit.",
                defaultRpe = 1
            ),
            contextSummary = "Kondisi agak capek — ramah pada tubuh jauh lebih penting dari durasi.",
            insightBadge = badge,
            insights = insights
        )
    }

    // -------------------------------------------------------------------------
    // 3. BUSY ("Waktu Mepet")
    // -------------------------------------------------------------------------
    private fun buildBusyRecommendation(
        sessions: List<WorkoutSession>,
        insights: WorkoutInsights,
        nowMillis: Long
    ): DailyRecommendation {
        val heavilyFatigued = (insights.lastSession != null && insights.lastSession.rpe >= 7) ||
            insights.consecutiveActiveDays >= 4

        // 3a. Weekly strength < 2 and not heavily fatigued -> Quick Bodyweight / Dumbbell Circuit
        if (insights.weeklyStrengthCount < 2 && !heavilyFatigued) {
            val strengthStatus = if (insights.weeklyStrengthCount == 0) {
                "belum ada latihan beban minggu ini"
            } else {
                "baru ${insights.weeklyStrengthCount} sesi beban minggu ini"
            }
            val progressionHint = if (insights.readyForProgression) {
                " Karena 2 sesi beban terakhir terasa ringan (RPE ≤ 5), boleh tambah sedikit beban atau +2 reps di tiap set."
            } else {
                ""
            }
            val circuitItems = if (insights.isLowerBodyFatigued) {
                listOf(
                    DumbbellItem("Push-up (atau Knee Push-up)", 0.0, 3, 8),
                    DumbbellItem("Dumbbell Row", 5.0, 3, 10),
                    DumbbellItem("Plank", 0.0, 3, 30)
                )
            } else {
                listOf(
                    DumbbellItem("Push-up (atau Knee Push-up)", 0.0, 3, 8),
                    DumbbellItem("Bodyweight Squat", 0.0, 3, 12),
                    DumbbellItem("Glute Bridge", 0.0, 3, 12),
                    DumbbellItem("Plank", 0.0, 2, 30)
                )
            }
            val lowerNote = if (insights.isLowerBodyFatigued) {
                " Gerakan difokuskan ke tubuh atas & core agar kaki pulih dari ${describeSessionShort(insights.lowerBodyFatigueSource, nowMillis)}."
            } else {
                ""
            }
            val badge = when {
                insights.readyForProgression -> "🔥 2 sesi beban terakhir ringan — micro-circuit siap naik level!"
                insights.isLowerBodyFatigued -> "⚡ Waktu mepet • Kaki butuh pulih, fokus circuit tubuh atas"
                insights.weeklyStrengthCount == 0 -> "💡 Minggu ini belum ada latihan beban — cukup 12–15 mnt di rumah"
                else -> "⚡ Waktu mepet • Baru ${insights.weeklyStrengthCount} sesi beban minggu ini"
            }

            return DailyRecommendation(
                idealOption = SuggestionOption(
                    badge = BADGE_IDEAL,
                    title = "Quick Bodyweight / Dumbbell Circuit",
                    sportType = SportType.DUMBBELL,
                    estimatedDurationMin = 14,
                    durationLabel = "12–15 menit",
                    reason = "Jadwal padat dan $strengthStatus. Micro-workout di rumah tanpa perlu keluar pintu ini tetap menjaga kekuatanmu.$lowerNote",
                    relaxedGuidance = "Lakukan berurutan antar gerakan dengan istirahat singkat 30–45 detik. Praktis, cepat selesai, tanpa ribet ganti tempat.$progressionHint",
                    defaultRpe = 5,
                    suggestedDumbbellItems = circuitItems
                ),
                lightOption = SuggestionOption(
                    badge = BADGE_LIGHT,
                    title = "Plank & Push-up Singkat",
                    sportType = SportType.DUMBBELL,
                    estimatedDurationMin = 8,
                    durationLabel = "7–10 menit",
                    reason = "Super ringkas saat waktu sangat sempit — tetap mengisi porsi kekuatan ($strengthStatus) dalam kurang dari 10 menit.",
                    relaxedGuidance = "Cukup 2 set Knee Push-up/Push-up dan 2 set Plank 30 detik di sela pekerjaan. Cepat tapi langsung memutus mager.",
                    defaultRpe = 3,
                    suggestedDumbbellItems = listOf(
                        DumbbellItem("Push-up (atau Knee Push-up)", 0.0, 2, 8),
                        DumbbellItem("Plank", 0.0, 2, 30)
                    )
                ),
                contextSummary = "Micro-workout efisien 10–15 menit untuk melengkapi porsi beban.",
                insightBadge = badge,
                insights = insights
            )
        }

        // 3b. Weekly strength >= 2 (or heavily fatigued) -> Quick Brisk Walk / Short Jog
        val reasonIdeal = when {
            insights.consecutiveActiveDays >= 4 ->
                "Kamu sudah aktif ${insights.consecutiveActiveDays} hari beruntun dan jadwal padat — jalan santai cepat 15 menit menjaga sirkulasi tanpa menambah kelelahan."
            insights.lastSession != null && insights.lastSession.rpe >= 7 ->
                "Setelah sesi berat ${describeSessionForReason(insights.lastSession, nowMillis)} dan waktu mepet, cukup jalan santai 15 menit untuk bantu pemulihan."
            insights.isLowerBodyFatigued ->
                "Sesi beban minggu ini sudah ${insights.weeklyStrengthCount} kali, namun kaki masih pulih dari ${describeSessionShort(insights.lowerBodyFatigueSource, nowMillis)} — pilih jalan cepat santai."
            else ->
                "Latihan beban minggu ini sudah mantap (${insights.weeklyStrengthCount} sesi). Selipkan 15 menit jalan cepat atau jogging singkat agar kardio tetap terjaga."
        }

        val idealSport = if (heavilyFatigued || insights.isLowerBodyFatigued) {
            SportType.WALKING
        } else {
            SportType.RUNNING
        }
        val idealTitle = if (heavilyFatigued || insights.isLowerBodyFatigued) {
            "Jalan Cepat Santai 15 Menit"
        } else {
            "Jalan Cepat / Jogging Singkat"
        }

        val badge = when {
            insights.consecutiveActiveDays >= 4 ->
                "🌿 Sudah ${insights.consecutiveActiveDays} hari aktif beruntun — cukup jalan ringan 15 mnt"
            insights.lastSession != null && insights.lastSession.rpe >= 7 ->
                "💡 Pemulihan cepat pasca ${describeSessionShort(insights.lastSession, nowMillis)} (RPE ${insights.lastSession.rpe})"
            insights.readyForProgression ->
                "🔥 2 sesi beban terakhir ringan — hari ini selipkan kardio ringkas"
            else ->
                "⚡ Waktu mepet • Beban sudah ${insights.weeklyStrengthCount} sesi, selipkan kardio 15 mnt"
        }

        return DailyRecommendation(
            idealOption = SuggestionOption(
                badge = BADGE_IDEAL,
                title = idealTitle,
                sportType = idealSport,
                estimatedDurationMin = 15,
                durationLabel = "15 menit",
                reason = reasonIdeal,
                relaxedGuidance = "Set timer 7 menit pergi dan 7–8 menit pulang. Jaga napas tetap nyaman (talk test) tanpa perlu melihat pace.",
                defaultRpe = if (heavilyFatigued) 2 else 4
            ),
            lightOption = SuggestionOption(
                badge = BADGE_LIGHT,
                title = "Jalan Santai Keliling",
                sportType = SportType.WALKING,
                estimatedDurationMin = 10,
                durationLabel = "10 menit",
                reason = if (sessions.isEmpty()) {
                    "Sepuluh menit berjalan santai cukup untuk memulai kebiasaan baik di sela jadwal padat."
                } else {
                    "Tetap jaga konsistensi (${insights.weeklyCardioCount + insights.weeklyStrengthCount} sesi minggu ini) dengan jalan ringan 10 menit di sekitar lokasi."
                },
                relaxedGuidance = "Jauhkan layar ponsel sejenak, jalan keliling halaman atau blok terdekat sambil merilekskan bahu.",
                defaultRpe = 2
            ),
            contextSummary = "Sesi kardio ringkas 10–15 menit yang pas di tengah jadwal padat.",
            insightBadge = badge,
            insights = insights
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private fun isLowerBodyTaxingSession(session: WorkoutSession): Boolean {
        if (session.sportType == SportType.RUNNING || session.sportType == SportType.BADMINTON) {
            if (session.rpe >= 7 || session.durationMin >= 45) return true
        }
        if (session.sportType == SportType.DUMBBELL) {
            val hasLegMovement = session.dumbbellItems.any { item ->
                val lower = item.exerciseName.lowercase()
                lowerBodyKeywords.any { keyword -> lower.contains(keyword) }
            }
            if (hasLegMovement) return true
        }
        return false
    }

    private fun describeSessionForReason(session: WorkoutSession?, nowMillis: Long): String {
        if (session == null) return "latihan kaki terakhir"
        val whenWord = relativeDayWord(session.startedAtIso, nowMillis)
        return if (session.sportType == SportType.DUMBBELL) {
            "latihan kaki (${session.durationMin} mnt, RPE ${session.rpe}) $whenWord"
        } else {
            "${session.sportType.displayName} ${session.durationMin} mnt $whenWord (RPE ${session.rpe})"
        }
    }

    private fun describeSessionShort(session: WorkoutSession?, nowMillis: Long): String {
        if (session == null) return "latihan kaki terakhir"
        val whenWord = relativeDayWord(session.startedAtIso, nowMillis)
        return if (session.sportType == SportType.DUMBBELL) {
            "latihan kaki $whenWord"
        } else {
            "${session.sportType.displayName} $whenWord"
        }
    }

    private fun relativeDayWord(isoString: String, nowMillis: Long): String {
        val millis = DateTimeHelper.parseIsoToMillis(isoString) ?: return "terakhir"
        return when (DateTimeHelper.calendarDaysBetween(millis, nowMillis)) {
            0 -> "hari ini"
            1 -> "kemarin"
            else -> "2 hari lalu"
        }
    }
}
