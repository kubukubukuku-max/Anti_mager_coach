package com.example.data

data class ExerciseGuide(
    val name: String,
    val targetMuscle: String,
    val isBodyweight: Boolean,
    val steps: List<String>,
    val safetyTip: String,
    val youtubeQuery: String
)

object ExerciseGuides {

    val bodyweightBasic: List<ExerciseGuide> = listOf(
        ExerciseGuide(
            name = "Push-up (atau Knee Push-up)",
            targetMuscle = "Dada, Bahu Depan & Trisep",
            isBodyweight = true,
            steps = listOf(
                "Letakkan kedua telapak tangan sedikit lebih lebar dari bahu di lantai atau matras.",
                "Luruskan tubuh dari kepala sampai tumit (atau tumpukan lutut di lantai untuk versi Knee Push-up).",
                "Tekuk siku secara perlahan mengarah agak ke belakang (~45°) sampai dada mendekati lantai.",
                "Dorong kembali ke atas sambil membuang napas dan menjaga perut tetap aktif."
            ),
            safetyTip = "Hindari pinggul turun melengkung ke bawah; kalau punggung bawah terasa tidak nyaman, langsung pindah ke Knee Push-up atau Wall Push-up.",
            youtubeQuery = "cara push up dan knee push up pemula yang benar"
        ),
        ExerciseGuide(
            name = "Plank",
            targetMuscle = "Core (Perut), Punggung & Bahu",
            isBodyweight = true,
            steps = listOf(
                "Bertumpu pada kedua lengan bawah dengan siku sejajar tepat di bawah bahu.",
                "Luruskan kaki ke belakang dan kencangkan perut serta bokong agar tubuh membentuk satu garis lurus.",
                "Arahkan pandangan ke lantai di antara kedua tangan agar leher tetap rileks.",
                "Tahan posisi sambil bernapas teratur dan pelan selama durasi yang ditentukan."
            ),
            safetyTip = "Jangan menahan napas dan jangan biarkan pinggang melengkung turun. Lebih baik durasi singkat dengan postur rapi daripada lama tapi pegal di pinggang.",
            youtubeQuery = "cara plank pemula yang benar tanpa sakit pinggang"
        ),
        ExerciseGuide(
            name = "Bodyweight Squat",
            targetMuscle = "Paha Depan, Bokong & Pinggul",
            isBodyweight = true,
            steps = listOf(
                "Berdiri dengan kaki selebar bahu dan ujung jari kaki sedikit mengarah keluar.",
                "Dorong pinggul ke belakang sambil menekuk lutut seperti hendak duduk di kursi imajiner.",
                "Turun senyamannya sambil menjaga dada tetap tegak dan tumit menapak penuh di lantai.",
                "Dorong lantai lewat tumit untuk berdiri kembali ke posisi awal."
            ),
            safetyTip = "Pastikan lutut mengikuti arah ujung kaki dan tidak jatuh ke dalam. Kedalaman squat bebas disesuaikan dengan kenyamanan lututmu.",
            youtubeQuery = "cara bodyweight squat pemula yang benar"
        ),
        ExerciseGuide(
            name = "Glute Bridge",
            targetMuscle = "Bokong, Paha Belakang & Core",
            isBodyweight = true,
            steps = listOf(
                "Berbaring telentang di matras dengan lutut ditekuk dan telapak kaki menapak selebar pinggul.",
                "Letakkan kedua tangan rileks di samping tubuh dengan telapak menghadap ke bawah.",
                "Dorong tumit ke lantai dan angkat pinggul ke atas sampai bahu, pinggul, dan lutut sejajar.",
                "Tahan 1 detik di puncak sambil mengencangkan bokong, lalu turunkan pinggul perlahan."
            ),
            safetyTip = "Fokus dorong dari bokong dan tumit, jangan mendorong terlalu tinggi sampai punggung bawah melengkung berlebihan.",
            youtubeQuery = "cara glute bridge pemula yang benar"
        ),
        ExerciseGuide(
            name = "Bird-Dog",
            targetMuscle = "Core, Punggung Bawah & Keseimbangan",
            isBodyweight = true,
            steps = listOf(
                "Ambil posisi merangkak (tabletop) dengan tangan di bawah bahu dan lutut di bawah pinggul.",
                "Luruskan tangan kanan ke depan bersamaan dengan meluruskan kaki kiri ke belakang secara perlahan.",
                "Tahan 1–2 detik sambil menjaga pinggul tetap rata sejajar lantai.",
                "Kembali ke posisi awal dengan kontrol penuh, lalu ulangi untuk sisi berlawanan."
            ),
            safetyTip = "Gerakkan dengan tempo lambat dan stabil; tidak perlu mengangkat kaki terlalu tinggi supaya pinggang tidak ikut memutar.",
            youtubeQuery = "cara bird dog exercise pemula core stability"
        )
    )

    val dumbbellGuides: List<ExerciseGuide> = listOf(
        ExerciseGuide(
            name = "Dumbbell Floor Press",
            targetMuscle = "Dada, Bahu Depan & Trisep",
            isBodyweight = false,
            steps = listOf(
                "Berbaring telentang di lantai dengan lutut ditekuk dan kedua telapak kaki menapak mantap.",
                "Pegang sepasang dumbbell dengan siku menempel lantai membentuk sudut sekitar 45° dari badan.",
                "Dorong kedua dumbbell lurus ke atas dada secara bersamaan sambil membuang napas.",
                "Turunkan perlahan sampai bagian belakang lengan kembali menyentuh lantai dengan lembut."
            ),
            safetyTip = "Jangan membanting siku ke lantai saat turun; tahan perlahan agar sendi bahu tetap aman dan nyaman.",
            youtubeQuery = "dumbbell floor press proper form beginner"
        ),
        ExerciseGuide(
            name = "Bent-Over Row",
            targetMuscle = "Punggung Atas, Lats & Bisep",
            isBodyweight = false,
            steps = listOf(
                "Berdiri selebar pinggul sambil memegang dumbbell di kedua tangan, lalu tekuk lutut sedikit.",
                "Condongkan badan ke depan dari pinggul (~45°) dengan punggung tetap lurus dan dada terbuka.",
                "Tarik kedua dumbbell ke arah pinggang sambil merapatkan tulang belikat ke belakang.",
                "Turunkan dumbbell secara perlahan ke posisi awal tanpa mengayunkan badan."
            ),
            safetyTip = "Jaga punggung tetap netral (tidak membungkuk). Pilih beban ringan-sedang agar tarikan benar-benar terasa di punggung, bukan pinggang.",
            youtubeQuery = "dumbbell bent over row proper form pemula"
        ),
        ExerciseGuide(
            name = "Overhead Press",
            targetMuscle = "Bahu, Trisep & Stabilitas Core",
            isBodyweight = false,
            steps = listOf(
                "Berdiri tegak (atau duduk) sambil memegang dumbbell setinggi bahu dengan telapak menghadap ke depan.",
                "Kencangkan otot perut dan bokong agar punggung bawah tetap stabil.",
                "Dorong kedua dumbbell lurus ke atas kepala hingga lengan hampir lurus penuh.",
                "Turunkan kembali ke posisi bahu dengan gerakan halus dan terkontrol."
            ),
            safetyTip = "Hindari melengkungkan punggung bawah saat mendorong ke atas. Jika terasa berat, kurangi beban atau lakukan satu tangan bergantian.",
            youtubeQuery = "dumbbell overhead shoulder press proper form beginner"
        ),
        ExerciseGuide(
            name = "Russian Twist",
            targetMuscle = "Perut Samping (Obliques) & Core",
            isBodyweight = false,
            steps = listOf(
                "Duduk di matras dengan lutut ditekuk dan tumit menapak lantai, lalu condongkan badan sedikit ke belakang.",
                "Pegang satu dumbbell ringan (atau tanpa beban) di depan dada dengan kedua tangan.",
                "Putar tubuh bagian atas perlahan ke kanan hingga beban mengarah ke samping pinggul.",
                "Kembali ke tengah lalu putar ke kiri sambil tetap bernapas teratur."
            ),
            safetyTip = "Pertahankan tumit tetap menapak lantai untuk versi ramah pemula dan jaga dada tetap tegak agar pinggang tidak membungkuk.",
            youtubeQuery = "russian twist dumbbell pemula cara benar"
        ),
        ExerciseGuide(
            name = "Goblet Squat",
            targetMuscle = "Paha Depan, Bokong & Core",
            isBodyweight = false,
            steps = listOf(
                "Berdiri selebar bahu dan pegang satu ujung dumbbell secara vertikal rapat di depan dada.",
                "Tarik napas, dorong pinggul ke belakang dan tekuk lutut sambil menjaga dada tetap tegak.",
                "Turun hingga paha kira-kira sejajar lantai atau batas nyaman lututmu.",
                "Dorong tumit kuat ke lantai untuk berdiri kembali sambil membuang napas."
            ),
            safetyTip = "Dekatkan dumbbell ke dada agar punggung tidak tertarik ke depan, dan jaga kedua tumit selalu menapak penuh di lantai.",
            youtubeQuery = "dumbbell goblet squat proper form beginner"
        ),
        ExerciseGuide(
            name = "Romanian Deadlift",
            targetMuscle = "Paha Belakang (Hamstring) & Bokong",
            isBodyweight = false,
            steps = listOf(
                "Berdiri selebar pinggul dengan dumbbell di depan paha dan lutut sedikit ditekuk lembut (soft knees).",
                "Dorong pinggul lurus ke belakang sambil menurunkan dumbbell dekat menyusuri bagian depan kaki.",
                "Berhenti saat terasa tarikan nyaman di paha belakang (biasanya di sekitar bawah lutut/pertengahan tulang kering).",
                "Dorong pinggul maju kembali ke posisi berdiri tegak sambil mengencangkan bokong."
            ),
            safetyTip = "Ini gerakan engsel pinggul (hip hinge), bukan membungkukkan punggung. Jaga punggung selalu rata dan beban tetap dekat dengan kaki.",
            youtubeQuery = "dumbbell romanian deadlift beginner form"
        ),
        ExerciseGuide(
            name = "Reverse Lunge",
            targetMuscle = "Paha, Bokong & Keseimbangan",
            isBodyweight = false,
            steps = listOf(
                "Berdiri tegak sambil memegang dumbbell di kedua sisi tubuh (atau tanpa beban dulu).",
                "Langkahkan satu kaki ke belakang dengan kontrol lalu turunkan pinggul perlahan hingga kedua lutut membentuk ~90°.",
                "Jaga berat badan seimbang di kaki depan dengan dada tetap tegak.",
                "Dorong kaki depan untuk kembali berdiri, lalu bergantian dengan kaki satunya."
            ),
            safetyTip = "Melangkah ke belakang lebih ramah di lutut dibanding lunge ke depan. Kamu boleh berpegangan dinding ringan jika sedang melatih keseimbangan.",
            youtubeQuery = "dumbbell reverse lunge proper form beginner"
        ),
        ExerciseGuide(
            name = "Calf Raise",
            targetMuscle = "Otot Betis & Stabilitas Pergelangan Kaki",
            isBodyweight = false,
            steps = listOf(
                "Berdiri tegak dengan kaki selebar pinggul sambil memegang dumbbell di samping tubuh.",
                "Angkat kedua tumit perlahan ke atas setinggi mungkin bertumpu pada bola telapak kaki.",
                "Tahan sejenak selama 1 detik di posisi puncak agar otot betis bekerja optimal.",
                "Turunkan tumit kembali ke lantai secara pelan dan terkontrol."
            ),
            safetyTip = "Hindari memantul cepat naik-turun. Jika butuh stabilitas ekstra, pegang satu dumbbell saja dan gunakan tangan lain untuk menyentuh dinding.",
            youtubeQuery = "standing dumbbell calf raise proper form"
        ),
        ExerciseGuide(
            name = "Dumbbell Row",
            targetMuscle = "Punggung, Lats & Lengan",
            isBodyweight = false,
            steps = listOf(
                "Tumpukan satu tangan dan lutut (atau satu tangan di kursi/bangku stabil) dengan punggung datar.",
                "Pegang dumbbell di tangan bebas lalu biarkan lengan menggantung lurus ke bawah.",
                "Tarik dumbbell mendekati pinggul sambil membawa siku ke belakang rapat di sisi badan.",
                "Turunkan kembali secara perlahan sebelum mengulangi repetisi berikutnya."
            ),
            safetyTip = "Bayangkan menarik lewat siku, bukan mengangkat dengan bahu. Jaga leher rileks dan bahu tidak naik mendekati telinga.",
            youtubeQuery = "single arm dumbbell row beginner proper form"
        ),
        ExerciseGuide(
            name = "Bicep Curl",
            targetMuscle = "Lengan Depan (Bisep) & Lengan Bawah",
            isBodyweight = false,
            steps = listOf(
                "Berdiri tegak memegang sepasang dumbbell di sisi badan dengan telapak tangan menghadap ke depan.",
                "Kunci posisi siku tetap dekat di samping rusuk sepanjang gerakan.",
                "Tekuk siku untuk mengangkat dumbbell perlahan ke arah bahu sambil membuang napas.",
                "Turunkan dumbbell perlahan ke posisi semula tanpa mengayunkan punggung."
            ),
            safetyTip = "Jika badan ikut terayun ke belakang, itu tanda beban terlalu berat. Pilih beban ringan agar siku tetap stabil dan kontrol tetap rapi.",
            youtubeQuery = "dumbbell bicep curl proper form pemula"
        )
    )

    val allGuides: List<ExerciseGuide> = bodyweightBasic + dumbbellGuides

    fun isTimedExercise(exerciseName: String): Boolean {
        return exerciseName.contains("plank", ignoreCase = true)
    }

    fun findByName(exerciseName: String): ExerciseGuide {
        val normalized = exerciseName.trim().lowercase()
        allGuides.firstOrNull { it.name.lowercase() == normalized }?.let { return it }

        // Helpful alias matching for presets & coaching suggestions
        val aliasMatch = when {
            normalized.contains("push-up") || normalized.contains("push up") -> "Push-up (atau Knee Push-up)"
            normalized.contains("plank") -> "Plank"
            normalized.contains("bodyweight squat") -> "Bodyweight Squat"
            normalized.contains("glute bridge") -> "Glute Bridge"
            normalized.contains("bird-dog") || normalized.contains("bird dog") -> "Bird-Dog"
            normalized.contains("floor press") -> "Dumbbell Floor Press"
            normalized.contains("bent-over") || normalized.contains("bent over") -> "Bent-Over Row"
            normalized.contains("overhead") || normalized.contains("shoulder press") || normalized.contains("dumbbell press") -> "Overhead Press"
            normalized.contains("russian twist") -> "Russian Twist"
            normalized.contains("goblet squat") -> "Goblet Squat"
            normalized.contains("romanian") || normalized.contains("deadlift") -> "Romanian Deadlift"
            normalized.contains("lunge") -> "Reverse Lunge"
            normalized.contains("calf") -> "Calf Raise"
            normalized.contains("row") -> "Dumbbell Row"
            normalized.contains("curl") -> "Bicep Curl"
            else -> null
        }

        if (aliasMatch != null) {
            allGuides.firstOrNull { it.name == aliasMatch }?.let { return it }
        }

        // Clean fallback for any custom user-typed exercise
        return ExerciseGuide(
            name = exerciseName.trim().ifEmpty { "Gerakan Latihan" },
            targetMuscle = "Kebugaran & Penguatan Otot",
            isBodyweight = false,
            steps = listOf(
                "Atur posisi awal yang stabil dengan dada terbuka dan otot perut sedikit aktif.",
                "Lakukan gerakan dengan tempo pelan dan terkontrol sepanjang rentang gerak yang nyaman.",
                "Jaga napas tetap mengalir teratur — buang napas saat mendorong atau mengangkat beban.",
                "Istirahat 45–60 detik antar set agar teknik tetap rapi."
            ),
            safetyTip = "Mulai dari beban ringan atau tanpa beban terlebih dahulu. Hentikan gerakan jika muncul nyeri tajam di sendi.",
            youtubeQuery = "${exerciseName.trim()} tutorial form pemula"
        )
    }
}
