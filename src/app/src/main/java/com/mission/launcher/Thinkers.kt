package com.mission.launcher

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** Philosophy optional revision cards: one thinker a day, key terms and the main idea in Hindi. */
object Thinkers {
    class P(val name: String, val school: String, val terms: List<String>, val idea: String, val use: String)

    val all =
        listOf(
            P("Plato", "Western | Greek Idealism", listOf("Theory of Forms", "Knowledge as recollection", "Ideal State"), "वास्तविक जगत् Forms की छाया है; ज्ञान (episteme) मत (doxa) से भिन्न है। आत्मा के तीन भाग: तर्क, साहस, इच्छा।", "Plato: Knowledge, Forms, Justice, Soul"),
            P("Aristotle", "Western | Greek Realism", listOf("Four causes", "Matter and form", "Potentiality-Actuality"), "Form वस्तु के भीतर है, अलग लोक में नहीं। चार कारण: भौतिक, रूपात्मक, निमित्त, अंतिम (teleology)।", "Aristotle: Causation, Substance, Virtue ethics"),
            P("Descartes", "Rationalism", listOf("Cogito ergo sum", "Methodic doubt", "Mind-body dualism", "Innate ideas"), "संदेह की पद्धति से 'मैं सोचता हूँ, इसलिए मैं हूँ' तक। मन और शरीर दो भिन्न द्रव्य; सम्पर्क का प्रश्न (pineal gland)।", "Descartes: Method, God proofs, Dualism"),
            P("Spinoza", "Rationalism", listOf("One substance", "Attributes: thought and extension", "Parallelism", "Pantheism"), "एक ही द्रव्य (God/Nature), मन और शरीर उसके दो गुण; समानांतरवाद से द्वैतवाद की समस्या का हल।", "Spinoza: Substance, Mind-body"),
            P("Leibniz", "Rationalism", listOf("Monads", "Pre-established harmony", "Principle of sufficient reason"), "अनेक चिदणु (monads), खिड़कीविहीन; पूर्व-स्थापित सामंजस्य; विश्लेषणात्मक और संश्लेषणात्मक सत्य का भेद।", "Leibniz: Monads, Truths of reason and fact"),
            P("Locke", "British Empiricism", listOf("Tabula rasa", "Primary and secondary qualities", "Substance as substratum"), "जन्मजात विचार नहीं; सारा ज्ञान अनुभव से। मूल और गौण गुण; द्रव्य 'अज्ञात आधार'।", "Locke: Ideas, Qualities, Substance"),
            P("Berkeley", "British Empiricism", listOf("Esse est percipi", "Subjective idealism", "Rejection of matter"), "होना = प्रत्यक्ष होना; भौतिक द्रव्य का खंडन; ईश्वर सतत प्रेक्षक।", "Berkeley: Idealism, Criticism of Locke"),
            P("Hume", "British Empiricism", listOf("Impressions and ideas", "Causation as habit", "Fork: relations of ideas / matters of fact", "Scepticism"), "कारणता केवल आदत और अपेक्षा है, आवश्यक संबंध नहीं; आत्मा 'प्रत्यक्षों का पुंज'।", "Hume: Causation, Self, Induction"),
            P("Kant", "Critical Philosophy", listOf("Synthetic a priori", "Categories", "Phenomena and noumena", "Categorical imperative"), "ज्ञान की संभावना: संवेदना और बुद्धि का योग। 'बिना सामग्री के विचार रिक्त, बिना अवधारणा के अंतर्दृष्टि अंधी'।", "Kant: Synthetic a priori, Categories, Ethics"),
            P("Hegel", "German Idealism", listOf("Dialectic", "Absolute Spirit", "Thesis-Antithesis-Synthesis"), "यथार्थ विचार की द्वंद्वात्मक गति है; जो बुद्धिसम्मत है वही वास्तविक है।", "Hegel: Dialectic, Absolute"),
            P("Moore", "Analytic | Realism", listOf("Refutation of idealism", "Common sense", "Naturalistic fallacy", "Open question argument"), "'Good' अपरिभाष्य सरल गुण है; आदर्शवाद का खंडन सामान्य बोध के आधार पर।", "Moore: Defence of common sense, Ethics"),
            P("Russell", "Analytic | Logical Atomism", listOf("Theory of descriptions", "Logical atomism", "Knowledge by acquaintance and description", "Sense data"), "भाषा का तार्किक विश्लेषण; वर्णन-सिद्धांत से अर्थ की समस्याओं का हल।", "Russell: Descriptions, Atomism, Perception"),
            P("Wittgenstein", "Analytic | Language", listOf("Picture theory", "Language games", "Private language argument", "Meaning is use"), "पूर्व: भाषा तथ्यों का चित्र; बाद में: अर्थ = प्रयोग, भाषा-खेल, निजी भाषा असंभव।", "Wittgenstein: Tractatus, Investigations"),
            P("Ayer", "Logical Positivism", listOf("Verification principle", "Emotivism", "Elimination of metaphysics"), "अर्थपूर्ण वाक्य या तो विश्लेषणात्मक या अनुभव-सत्यापनीय; नैतिक कथन भावों की अभिव्यक्ति।", "Ayer: Verification, Ethics"),
            P("Sartre", "Existentialism", listOf("Existence precedes essence", "Bad faith", "Freedom and responsibility", "Being-for-itself"), "मनुष्य पहले अस्तित्व में आता है, फिर स्वयं को बनाता है; स्वतंत्रता से बचना ही bad faith।", "Sartre: Freedom, Bad faith"),
            P("Kierkegaard", "Existentialism", listOf("Leap of faith", "Three stages", "Anxiety", "Subjectivity is truth"), "जीवन के तीन चरण: सौंदर्यात्मक, नैतिक, धार्मिक; विश्वास की छलांग।", "Kierkegaard: Stages, Faith"),
            P("Heidegger", "Existentialism | Phenomenology", listOf("Dasein", "Being-in-the-world", "Care", "Authenticity"), "सत्ता का प्रश्न; Dasein की संरचना: विश्व में होना, चिंता (Sorge), काल-बद्धता।", "Heidegger: Dasein, Being"),
            P("Husserl", "Phenomenology", listOf("Epoche", "Intentionality", "Eidetic reduction", "Lifeworld"), "'वस्तुओं की ओर लौटो'; ब्रैकेटिंग से शुद्ध चेतना की संरचना का अध्ययन।", "Husserl: Phenomenological method"),
            P("Shankaracharya", "Indian | Advaita Vedanta", listOf("Brahman satya jagat mithya", "Maya", "Adhyasa", "Jiva-Brahma aikya"), "ब्रह्म ही सत्य, जगत् मिथ्या (अनिर्वचनीय), जीव और ब्रह्म अभिन्न; ज्ञान से मोक्ष।", "Shankara: Maya, Brahman, Moksha"),
            P("Ramanuja", "Indian | Vishishtadvaita", listOf("Visheshana-visheshya", "Saguna Brahman", "Prapatti", "Bhakti"), "ब्रह्म सगुण; चित्-अचित् उसके शरीर; भक्ति और प्रपत्ति से मोक्ष।", "Ramanuja: Criticism of Maya, Brahman"),
            P("Madhva", "Indian | Dvaita", listOf("Five differences (panchabheda)", "Svatantra and paratantra", "Bhakti"), "ईश्वर, जीव, जगत् के बीच पाँच वास्तविक भेद; ईश्वर की कृपा से मोक्ष।", "Madhva: Five differences"),
            P("Buddha | Nagarjuna", "Indian | Buddhism", listOf("Four noble truths", "Pratityasamutpada", "Anatta", "Shunyata"), "दुःख का कारण तृष्णा; प्रतीत्यसमुत्पाद; अनात्म; नागार्जुन: शून्यता और माध्यमिक मार्ग।", "Buddhism: Dependent origination, No-self, Shunyata"),
            P("Mahavira | Jain", "Indian | Jainism", listOf("Anekantavada", "Syadvada", "Saptabhangi", "Tri-ratna"), "सत्य अनेक पक्षीय; स्यात् भाषा; सम्यक् दर्शन, ज्ञान, चारित्र।", "Jainism: Anekantavada, Syadvada"),
            P("Nyaya | Gautama", "Indian | Nyaya", listOf("Pramanas", "Anumana", "Hetvabhasa", "Asatkaryavada"), "चार प्रमाण: प्रत्यक्ष, अनुमान, उपमान, शब्द; हेत्वाभास; ईश्वर की सिद्धि।", "Nyaya: Pramana, Inference, God"),
            P("Samkhya | Kapila", "Indian | Samkhya", listOf("Purusha and Prakriti", "Satkaryavada", "Three gunas", "Evolution"), "दो तत्त्व: पुरुष और प्रकृति; सत्कार्यवाद; सत्त्व-रजस्-तमस्; विवेक ख्याति से कैवल्य।", "Samkhya: Satkaryavada, Evolution"),
            P("Gandhi", "Social and Political", listOf("Satyagraha", "Ahimsa", "Sarvodaya", "Swaraj", "Trusteeship"), "साधन-साध्य की शुद्धता; अहिंसा; ग्राम स्वराज; सर्वोदय।", "Gandhi: Non-violence, Satyagraha, Swaraj"),
            P("Ambedkar", "Social and Political", listOf("Annihilation of caste", "Social democracy", "Navayana Buddhism"), "जाति-उन्मूलन, सामाजिक लोकतंत्र; बंधुता; 'शिक्षित बनो, संगठित रहो, संघर्ष करो'।", "Ambedkar: Equality, Caste, Democracy"),
            P("Rawls", "Social and Political | Justice", listOf("Original position", "Veil of ignorance", "Difference principle", "Justice as fairness"), "न्याय के दो सिद्धांत: समान स्वतंत्रता, और विषमता केवल तब जब सबसे वंचित का हित हो।", "Rawls: Justice as fairness"),
        )

    fun today(): P = all[(Targets.today().toEpochDay() % all.size).toInt()]

    private fun body(c: Activity, p: P, col: Int): View {
        val box = c.vbox()
        box.addView(PyqUi.chip(c, p.school, col, true))
        box.addView(c.text(p.idea, 13.5f, 2, T.text), lp(-1, -2, 10))
        box.addView(c.text("Key terms", 12f, 3, T.text2), lp(-2, -2, 12))
        box.addView(c.chipRow(*p.terms.map { PyqUi.chip(c, it, col) }.toTypedArray()), lp(-1, -2, 4))
        box.addView(c.text("Where it appears: " + p.use, 11.5f, 1, T.textAccent(col)), lp(-1, -2, 12))
        return box
    }

    fun detail(a: Activity, p: P) {
        val col = T.palette[all.indexOf(p) % T.palette.size]
        val sv = ScrollView(a).apply { addView(body(a, p, col).apply { padDp(18, 6, 18, 6) }) }
        Dlg.builder(a).setTitle(p.name).setView(sv).setPositiveButton("Close", null).show()
    }

    fun list(a: Activity) {
        val names = all.map { it.name + "  |  " + it.school }.toTypedArray()
        Dlg.builder(a).setTitle("Philosophy thinkers (${all.size})").setItems(names) { _, i -> detail(a, all[i]) }.setNegativeButton("Close", null).show()
    }

    fun card(h: Host): View {
        val c = h.act
        val p = today()
        val col = T.palette[all.indexOf(p) % T.palette.size]
        val card = Card(c)
        card.addView(c.cardHeader("book", "Thinker of the day", col))
        card.addView(c.text(p.name, 20f, 3, T.textAccent(col)), lp(-2, -2, 6))
        card.addView(body(c, p, col), lp(-1, -2, 4))
        val row = c.hbox()
        row.addView(c.pill("All thinkers", "grid", false, col) { list(c) })
        card.addView(row, lp(-2, -2, 10))
        return card
    }
}
