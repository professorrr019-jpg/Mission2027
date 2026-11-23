package com.mission.launcher

/**
 * One clean Prelims syllabus: every subject has 15-20 distinct sub-topics, in study order, with no duplicates.
 * The long, repeating micro-theme lists that come from Zite (e.g. "Federalism & Centre-State" and "Federalism & Centre–State")
 * are merged into these sub-topics. PYQ counts, answers and ticks are carried over, nothing is lost.
 */
object SylCanon {
    private class T(val name: String, val rx: Regex?)

    private fun t(name: String, rx: String? = null) = T(name, rx?.let { Regex(it, RegexOption.IGNORE_CASE) })

    private class Spec(val topics: List<T>, val fallback: String)

    private val SPEC: Map<String, Spec> =
        linkedMapOf(
            "Polity" to
                Spec(
                    listOf(
                        t("Fundamental Duties", "Fundamental Duties|मूल कर्तव्य"),
                        t("Fundamental Rights", "Fundamental Rights|मौलिक अधिकार"),
                        t("Directive Principles (DPSP)", "Directive|नीति-निदेशक|DPSP"),
                        t("Emergency Provisions", "Emergency|आपातकालीन"),
                        t("Constitutional Amendments", "Amendment|संशोधन"),
                        t("Elections & Electoral Reforms", "Election|Electoral"),
                        t("Parliament & State Legislatures", "Parliament|Anti-Defection|Legislature"),
                        t("Union & State Executive", "Executive|President|Governor|Council of Ministers"),
                        t("Judiciary", "Judiciary"),
                        t("Federalism & Centre-State Relations", "Federal|Centre|संघ और क्षेत्र"),
                        t("Local Government & Panchayati Raj", "Local Government|Panchayat"),
                        t("Public Finance & Finance Commission", "Public Finance|Finance Commission"),
                        t("Social Justice & Vulnerable Sections", "Social Justice|Vulnerable"),
                        t("Constitutional & Statutory Bodies", "Statutory|Regulatory|वैधानिक|संवैधानिक निकाय|Advisory|Constitutional & "),
                        t("Citizenship, Schedules & Union Territories", "Citizenship|नागरिकता|अनुसूचियाँ|Schedule|Constitutional Provisions|Constitutional Articles"),
                        t("Constitution Making, Preamble & Framework", "Constitution|संविधान|प्रस्तावना|Framework|Development"),
                        t("Political Theory & Concepts", "Political|राजनीतिक"),
                        t("Governance, Policies & Current Affairs", null),
                    ),
                    "Governance, Policies & Current Affairs",
                ),
            "Economy" to
                Spec(
                    listOf(
                        t("Human Development & Social Sector", "Human|Social Sector|Social Indicators|Development Goals"),
                        t("Digital Payments & Financial Inclusion", "Digital Payments|Financial Inclusion"),
                        t("Poverty, Inequality & Inclusive Growth", "Poverty|Inequality|Inclusive|Household Resilience"),
                        t("Labour, Employment & Demography", "Labour|Employment|PLFS|Unemployment|Demograph|Dependency"),
                        t("Monetary Policy & RBI", "Monetary|Inflation Targeting|Policy-Rate|Liquidity"),
                        t("Fiscal Policy & Union Budget", "Fiscal|Budget|Public Expenditure|Public Finance"),
                        t("Taxation & GST", "Tax|GST"),
                        t("Inflation & Price Indices", "Inflation|Price Indices|CPI|Price"),
                        t("Money & Banking", "Bank|Money|Credit|Deposit|NPA|RBI"),
                        t("Financial & Capital Markets", "Financial|Capital Markets|Markets"),
                        t("External Sector & Balance of Payments", "External|Balance of Payments|Foreign"),
                        t("International Trade & WTO", "Trade|WTO|Customs"),
                        t("International Economic Institutions", "IMF|World Bank|International"),
                        t("Infrastructure, Industry & Services", "Infrastructure|Industry|IT & Services"),
                        t("Survey, Data & Index Interpretation", "Arithmetic|Interpretation|Concept Matching|Survey|Forecast|Release|Macro Analysis"),
                        t("National Income & Growth", "National|GDP|GVA|GNI|NDP|Growth|Output|Production|Income|Macro|Real vs Nominal|Expenditure Method|Factors|Long-run"),
                        t("Planning, Development & Basics", "Planning|Plans|Development|Indian Economy|Integrated|Business Cycle"),
                        t("Economy Miscellaneous", null),
                    ),
                    "Economy Miscellaneous",
                ),
            "Geography" to
                Spec(
                    listOf(
                        t("Astronomy & Earth-Sun System", "Astronomy|Earth.Sun"),
                        t("Maps, Cartography & Geospatial", "Cartograph|Maps"),
                        t("Disaster Management", "Disaster"),
                        t("Oceanography", "Oceanograph"),
                        t("Hydrology & Water Distribution", "Hydrology"),
                        t("Indian Rivers & Water Resources", "Rivers"),
                        t("Transport & Infrastructure Geography", "Transport"),
                        t("India: Political Geography & Borders", "Indian Political|India.*Political"),
                        t("World: Political Geography & Borders", "World Political"),
                        t("Biogeography, Vegetation & Soils", "Biogeography|Soils|Vegetation"),
                        t("Atmosphere & Climatology", "Climatolog|Atmosphere"),
                        t("India: Resources & Economic Geography", "India.*Resources|Resources"),
                        t("Economic & Mineral Geography", "Economic & Mineral"),
                        t("Human & Population Geography", "Human|Population|Demograph"),
                        t("India: Physical Geography", "India"),
                        t("Geomorphology & Landforms", "Geomorph|Physical Geography"),
                        t("World Regional Geography", null),
                    ),
                    "World Regional Geography",
                ),
            "History" to
                Spec(
                    listOf(
                        t("Indus Valley & Archaeology", "Indus|Archaeology|Epigraphy"),
                        t("Vedic Period", "Vedic"),
                        t("Mahajanapadas, Buddhism & Jainism", "Mahajanapada|Buddhism|Jain|Early Kingdoms"),
                        t("Mauryan Empire", "Mauryan"),
                        t("Gupta & Post-Gupta Period", "Gupta|Ancient India"),
                        t("South Indian & Regional Dynasties", "South Indian|Regional|Frontier|Vijayanagara"),
                        t("Delhi Sultanate", "Delhi"),
                        t("Mughal Empire", "Mughal"),
                        t("Bhakti & Sufi Movements", "Bhakti|Sufi"),
                        t("Medieval Society, Culture & Travellers", "Medieval|Foreign Travellers|Accounts"),
                        t("Advent of Europeans & Colonial Expansion", "Advent|European|British Expansion|Colonial"),
                        t("British Administration & Economic Policies", "British Administration|Modern India"),
                        t("Social & Religious Reform Movements", "Reform"),
                        t("Peasant, Tribal & Labour Movements", "Peasant|Tribal|Labour"),
                        t("Early Nationalism & INC (1885-1915)", "Early Nationalism"),
                        t("Gandhian Era & Mass Movements", "Gandhian"),
                        t("Revolutionary & Communist Movements", "Revolutionary|Communist"),
                        t("Constitutional Developments & Partition", "Constitutional|Transfer of Power|Partition"),
                        t("Post-Independence India", "Post-Independence"),
                        t("Philosophy, Science & Society", null),
                    ),
                    "Philosophy, Science & Society",
                ),
            "Environment" to
                Spec(
                    listOf(
                        t("Biogeochemical Cycles & Earth Systems", "Biogeochemical"),
                        t("Protected Areas & Wildlife", "Protected|Wildlife"),
                        t("Biodiversity & Conservation", "Biodiversity"),
                        t("Climate Change & Greenhouse Gases", "Climate"),
                        t("International Environmental Treaties & Summits", "Treat|Summit"),
                        t("Mineral & Resource Governance", "Mineral|Resource"),
                        t("Environmental Governance, Laws & Forest Rights", "Governance|Legislation|Forest Rights"),
                        t("Pollution & Waste Management", "Pollution|Waste"),
                        t("Water & Wetland Conservation", "Water|Wetland"),
                        t("Renewable Energy & Green Technology", "Renewable|Green"),
                        t("Energy & Environment", "Energy"),
                        t("Ecosystems & Ecology Concepts", null),
                        t("Environmental Impact Assessment & Institutions"),
                        t("Sustainable Development & Green Finance"),
                        t("Invasive Species & Wildlife Trade"),
                    ),
                    "Ecosystems & Ecology Concepts",
                ),
            "Agriculture" to
                Spec(
                    listOf(
                        t("Crops & Cropping Seasons", "Crops"),
                        t("Soils & Land Use", "Soils|Land Use"),
                        t("Organic & Sustainable Farming", "Biofertilizer|Organic|Sustainable"),
                        t("Rainfed & Integrated Farming", "Rainfed|Integrated"),
                        t("Food Security & Agri-Markets", "Food Security|Markets"),
                        t("Rural Credit & Schemes", "Credit|Schemes"),
                        t("Livestock, Fisheries & Allied Sectors", "Livestock|Fisheries|Allied"),
                        t("Agro-processing & Food Industry", "Agro-processing|By-products"),
                        t("Agro-biotechnology & Agroforestry", "Agro-biotech|Forestry"),
                        t("Agricultural Trade & Subsidies", "Trade"),
                        t("Rural Development Programmes", "Rural Development"),
                        t("Agricultural Policy & Reforms", null),
                        t("Irrigation & Water Management"),
                        t("Fertilisers, Seeds & Inputs"),
                        t("Land Reforms & Tenure"),
                    ),
                    "Agricultural Policy & Reforms",
                ),
            "Art" to
                Spec(
                    listOf(
                        t("Architecture & World Heritage", "Architecture"),
                        t("Sculpture & Iconography", "Sculpture"),
                        t("Painting & Visual Arts", "Painting"),
                        t("Classical Dance Forms", "Dance"),
                        t("Music", "Music"),
                        t("Theatre & Cinema", "Theatre|Cinema"),
                        t("Languages & Scripts", "Languages & Scripts|Scripts"),
                        t("Literature & Authors", "Literature|Languages"),
                        t("Philosophy & Religion", "Philosophy|Religion"),
                        t("Festivals, Crafts & Folk Traditions", "Festival|Craft"),
                        t("Tribes & Communities", "Tribes"),
                        t("Cultural Institutions & Awards", null),
                        t("UNESCO Intangible Heritage"),
                        t("Puppetry & Folk Performing Arts"),
                        t("GI Tags & Handicrafts"),
                    ),
                    "Cultural Institutions & Awards",
                ),
            "Current" to
                Spec(
                    listOf(
                        t("Awards & Honours", "Awards & Honours"),
                        t("Books & Authors", "Books"),
                        t("Cricket", "Cricket"),
                        t("Football", "Football"),
                        t("Tennis & Other Sports", "Tennis|Other Sports|Sports"),
                        t("Notable Personalities", "Personalities"),
                        t("Disasters & Humanitarian Issues", "Disaster|Humanitarian"),
                        t("Government Schemes & Initiatives", "Schemes"),
                        t("Summits & Conferences", "Summit|Conference"),
                        t("International Conflicts & Security", "Conflict|Security"),
                        t("Military Structure & Defence", "Military|Defence"),
                        t("Business & Economy in News", "Business"),
                        t("CSAT: Logical Reasoning", "Logical"),
                        t("CSAT: Quantitative Aptitude", "Quantitative"),
                        t("Reports & Indices in News", null),
                        t("CSAT: Reading Comprehension"),
                    ),
                    "Reports & Indices in News",
                ),
            "International" to
                Spec(
                    listOf(
                        t("United Nations & Peacekeeping", "United Nations|UN |Peacekeeping|Nobel"),
                        t("Arms Control & Nuclear Diplomacy", "Arms|Nuclear"),
                        t("Conflicts, Terrorism & Geopolitics", "Conflict|Terror|Geopolitical"),
                        t("Border, Maritime & Regional Disputes", "Border|Maritime|Dispute"),
                        t("Connectivity & Regional Cooperation", "Connectivity"),
                        t("Trade & Economic Diplomacy", "Trade"),
                        t("Energy & Resource Diplomacy", "Energy"),
                        t("Climate & Global Environmental Diplomacy", "Climate"),
                        t("Treaties, Agreements & Protocols", "Treat|Agreement|Protocol"),
                        t("International Institutions & Reports", "Institutions & Reports|Institutions"),
                        t("Regional Groupings (SAARC, ASEAN, BIMSTEC, EU)", "Regional|BIMSTEC|European Union|SAARC"),
                        t("International Organisations & Groupings", "Organisation|Organization|Groupings"),
                        t("Bilateral Relations, Visits & Outcomes", "Bilateral|Visits|Diplomacy"),
                        t("Development Partnerships & Migration", "Development|Migration|Interpol|Policing"),
                        t("Foreign Policy & Current Affairs", null),
                        t("Indian Diaspora & Soft Power"),
                    ),
                    "Foreign Policy & Current Affairs",
                ),
            "Science" to
                Spec(
                    listOf(
                        t("Physics", "Physics|Electrical Conductivity"),
                        t("Chemistry", "Chemistry"),
                        t("Biotechnology & Genetics", "Biotech|Genetic"),
                        t("Biology & Botany", "Biology|Botany"),
                        t("Health & Diseases", "Health"),
                        t("Space Technology & Astronomy", "Space|Astronomy"),
                        t("Defence Technology", "Defence"),
                        t("Computer & Information Technology", "Computer|Information Technology"),
                        t("Energy & Nuclear Technology", "Energy"),
                        t("Materials, Rare Earths & Engineering", "Materials|Rare Earth"),
                        t("S&T Institutions & Policy", "Institutions"),
                        t("History of Science & Technology", "History"),
                        t("Emerging Technology", "^Technology$"),
                        t("Everyday Science & Miscellaneous", null),
                        t("Nanotechnology & Robotics"),
                        t("Artificial Intelligence & Quantum"),
                    ),
                    "Everyday Science & Miscellaneous",
                ),
        )

    private fun key(subject: String): String? = SPEC.keys.firstOrNull { subject.startsWith(it) }

    private fun clean(s: String) = s.replace(Regex("&#8211;|&#8212;"), "-").replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"").trim()

    /** The single sub-topic a raw Zite micro-theme belongs to (the raw name when the subject is not known). */
    fun canon(subject: String, micro: String): String {
        val spec = key(subject)?.let { SPEC[it] } ?: return micro
        val m = clean(micro)
        return spec.topics.firstOrNull { it.rx != null && it.rx.containsMatchIn(m) }?.name ?: spec.fallback
    }

    /** Rebuilds the tree: canonical sub-topics in study order, sub-themes and PYQ counts merged. */
    fun apply(list: List<Subj>): List<Subj> =
        list.map { s ->
            val spec = key(s.name)?.let { SPEC[it] } ?: return@map s
            val buckets = LinkedHashMap<String, LinkedHashMap<String, Int>>()
            spec.topics.forEach { buckets[it.name] = LinkedHashMap() }
            s.micros.forEach { m ->
                val b = buckets.getValue(canon(s.name, m.name))
                m.subs.forEach { sub ->
                    val n = clean(sub.name).ifBlank { clean(m.name) }
                    b[n] = (b[n] ?: 0) + sub.q
                }
            }
            Subj(s.name, buckets.map { (name, subs) -> Micro(name, subs.map { Sub(it.key, it.value) }.sortedByDescending { it.q }) })
        }

    /** Merges raw "Subject|Micro" counters (answers given) into the canonical sub-topic key. */
    fun mergeResp(raw: Map<String, IntArray>): HashMap<String, IntArray> {
        val out = HashMap<String, IntArray>()
        raw.forEach { (k, v) ->
            val i = k.indexOf('|')
            val nk = if (i < 0) k else k.substring(0, i) + "|" + canon(k.substring(0, i), k.substring(i + 1))
            val cur = out.getOrPut(nk) { IntArray(3) }
            for (j in 0 until minOf(3, v.size)) cur[j] += v[j]
        }
        return out
    }

    /** Adds the canonical key for every tick made under an old micro-theme name. Old keys stay, so history is never lost. */
    fun migrateDone() {
        val done = Store.stringList("syl_done")
        if (done.isEmpty()) return
        val add = ArrayList<String>()
        done.forEach { k ->
            val i = k.indexOf('|')
            if (i < 0) return@forEach
            val rest = k.substring(i + 1)
            val j = rest.indexOf('|')
            val micro = if (j < 0) rest else rest.substring(0, j)
            val nk = k.substring(0, i) + "|" + canon(k.substring(0, i), micro) + (if (j < 0) "" else rest.substring(j))
            if (nk != k && nk !in done && nk !in add) add.add(nk)
        }
        if (add.isNotEmpty()) Store.setStringList("syl_done", done + add)
    }
}
