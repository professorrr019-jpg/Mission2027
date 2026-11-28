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

    /** Well-attested lines only: who, quote, Hindi meaning, source, colour-coded keywords. */
    class Q(val who: String, val text: String, val hi: String, val src: String, val kw: List<String>)

    val quotes: List<Q> =
        listOf(
            Q("Socrates", "The unexamined life is not worth living.", "अपरीक्षित जीवन जीने योग्य नहीं है।", "Plato, Apology", listOf("unexamined", "life")),
            Q("Socrates", "I know that I know nothing.", "मैं जानता हूँ कि मैं कुछ नहीं जानता।", "Apology (traditional paraphrase)", listOf("know", "nothing")),
            Q("Plato", "Philosophy begins in wonder.", "दर्शन का आरंभ विस्मय से होता है।", "Theaetetus 155d", listOf("wonder", "Philosophy")),
            Q("Plato", "The beginning is the most important part of the work.", "आरंभ कार्य का सबसे महत्वपूर्ण भाग है।", "Republic", listOf("beginning")),
            Q("Aristotle", "Man is by nature a political animal.", "मनुष्य स्वभाव से ही राजनीतिक (सामाजिक) प्राणी है।", "Politics", listOf("political", "nature")),
            Q("Aristotle", "All men by nature desire to know.", "सभी मनुष्य स्वभाव से जानना चाहते हैं।", "Metaphysics, Book I", listOf("desire", "know")),
            Q("Aristotle", "Virtue is a mean between two extremes.", "गुण दो अतियों के बीच का मध्य मार्ग है।", "Nicomachean Ethics (Doctrine of the Mean)", listOf("Virtue", "mean", "extremes")),
            Q("Descartes", "Cogito, ergo sum: I think, therefore I am.", "मैं सोचता हूँ, इसलिए मैं हूँ।", "Principles of Philosophy", listOf("think", "am")),
            Q("Descartes", "It is not enough to have a good mind; the main thing is to use it well.", "अच्छा मस्तिष्क होना पर्याप्त नहीं; मुख्य बात है उसका सही उपयोग।", "Discourse on Method", listOf("mind", "use")),
            Q("Spinoza", "All things excellent are as difficult as they are rare.", "जो कुछ उत्कृष्ट है वह जितना दुर्लभ है उतना ही कठिन भी।", "Ethics, Part V", listOf("excellent", "difficult", "rare")),
            Q("Spinoza", "I have striven not to laugh at, lament, or detest human actions, but to understand them.", "मैंने मानवीय कर्मों पर हँसने, रोने या घृणा करने के बजाय उन्हें समझने का प्रयास किया।", "Political Treatise", listOf("understand")),
            Q("Leibniz", "This is the best of all possible worlds.", "यह सभी संभव जगतों में सर्वश्रेष्ठ है।", "Theodicy", listOf("best", "possible worlds")),
            Q("Locke", "No man's knowledge can go beyond his experience.", "किसी का ज्ञान उसके अनुभव से आगे नहीं जा सकता।", "Essay Concerning Human Understanding", listOf("knowledge", "experience")),
            Q("Berkeley", "Esse est percipi: to be is to be perceived.", "होना ही प्रत्यक्ष होना है।", "Principles of Human Knowledge", listOf("perceived")),
            Q("Hume", "Reason is, and ought only to be, the slave of the passions.", "बुद्धि भावनाओं की दासी है, और उसे यही होना चाहिए।", "A Treatise of Human Nature", listOf("Reason", "passions")),
            Q("Hume", "Custom is the great guide of human life.", "अभ्यास (आदत) मानव जीवन का महान मार्गदर्शक है।", "An Enquiry Concerning Human Understanding", listOf("Custom", "guide")),
            Q("Kant", "Thoughts without content are empty, intuitions without concepts are blind.", "बिना सामग्री के विचार रिक्त, बिना अवधारणा के अंतर्दृष्टि अंधी।", "Critique of Pure Reason", listOf("empty", "blind", "concepts")),
            Q("Kant", "Act only according to that maxim whereby you can at the same time will that it should become a universal law.", "केवल उसी सिद्धांत पर कर्म करो जिसे तुम सार्वभौमिक नियम बनाना चाह सको।", "Groundwork of the Metaphysics of Morals", listOf("maxim", "universal law")),
            Q("Kant", "Two things fill the mind with admiration: the starry heavens above me and the moral law within me.", "दो चीज़ें मन को विस्मय से भरती हैं: ऊपर तारों भरा आकाश और भीतर नैतिक नियम।", "Critique of Practical Reason", listOf("starry heavens", "moral law")),
            Q("Kant", "Sapere aude: have courage to use your own understanding.", "अपनी बुद्धि के प्रयोग का साहस करो।", "What is Enlightenment?", listOf("courage", "understanding")),
            Q("Hegel", "What is rational is actual, and what is actual is rational.", "जो बुद्धिसम्मत है वही वास्तविक है, और जो वास्तविक है वही बुद्धिसम्मत।", "Philosophy of Right", listOf("rational", "actual")),
            Q("Hegel", "The owl of Minerva spreads its wings only with the falling of the dusk.", "मिनर्वा का उल्लू संध्या होने पर ही उड़ान भरता है (दर्शन घटना के बाद समझता है)।", "Philosophy of Right", listOf("owl of Minerva")),
            Q("Bentham", "It is the greatest happiness of the greatest number that is the measure of right and wrong.", "अधिकतम लोगों का अधिकतम सुख ही सही-गलत की कसौटी है।", "A Fragment on Government", listOf("greatest happiness", "greatest number")),
            Q("J. S. Mill", "It is better to be a human being dissatisfied than a pig satisfied.", "संतुष्ट सूअर होने से असंतुष्ट मनुष्य होना बेहतर है।", "Utilitarianism", listOf("dissatisfied", "satisfied")),
            Q("Marx", "Philosophers have only interpreted the world; the point is to change it.", "दार्शनिकों ने केवल जगत् की व्याख्या की है; बात उसे बदलने की है।", "Theses on Feuerbach", listOf("interpreted", "change")),
            Q("Marx", "Religion is the opium of the people.", "धर्म जनता की अफ़ीम है।", "Critique of Hegel's Philosophy of Right", listOf("Religion", "opium")),
            Q("Nietzsche", "He who has a why to live can bear almost any how.", "जिसके पास जीने का कारण (क्यों) है, वह लगभग किसी भी 'कैसे' को सह सकता है।", "Twilight of the Idols", listOf("why", "how")),
            Q("Kierkegaard", "Life can only be understood backwards; but it must be lived forwards.", "जीवन को समझा पीछे देखकर जाता है, पर जिया आगे बढ़कर जाता है।", "Journals", listOf("backwards", "forwards")),
            Q("Wittgenstein", "The limits of my language mean the limits of my world.", "मेरी भाषा की सीमाएँ ही मेरे जगत् की सीमाएँ हैं।", "Tractatus 5.6", listOf("language", "limits", "world")),
            Q("Wittgenstein", "Whereof one cannot speak, thereof one must be silent.", "जिसके बारे में बोला नहीं जा सकता, उस पर मौन रहना चाहिए।", "Tractatus 7", listOf("speak", "silent")),
            Q("Wittgenstein", "Philosophy is a battle against the bewitchment of our intelligence by means of language.", "दर्शन भाषा द्वारा हमारी बुद्धि के सम्मोहन के विरुद्ध संघर्ष है।", "Philosophical Investigations 109", listOf("bewitchment", "language")),
            Q("Russell", "Philosophy is to be studied for the sake of the questions themselves.", "दर्शन का अध्ययन उत्तरों के लिए नहीं, प्रश्नों के लिए किया जाना चाहिए।", "The Problems of Philosophy", listOf("questions")),
            Q("Popper", "Our knowledge can only be finite, while our ignorance must necessarily be infinite.", "हमारा ज्ञान सीमित हो सकता है, पर अज्ञान अनंत होना ही है।", "Conjectures and Refutations", listOf("finite", "infinite", "ignorance")),
            Q("Sartre", "Man is condemned to be free.", "मनुष्य स्वतंत्र होने के लिए अभिशप्त है।", "Existentialism is a Humanism", listOf("condemned", "free")),
            Q("Sartre", "Existence precedes essence.", "अस्तित्व सार से पहले है।", "Existentialism is a Humanism", listOf("Existence", "essence")),
            Q("Camus", "One must imagine Sisyphus happy.", "सिसिफस को सुखी मानना होगा।", "The Myth of Sisyphus", listOf("Sisyphus", "happy")),
            Q("Simone de Beauvoir", "One is not born, but rather becomes, a woman.", "कोई स्त्री पैदा नहीं होती, बनाई जाती है।", "The Second Sex", listOf("born", "becomes")),
            Q("Heidegger", "Language is the house of Being.", "भाषा सत्ता का घर है।", "Letter on Humanism", listOf("Language", "Being")),
            Q("Husserl", "Zu den Sachen selbst: to the things themselves!", "वस्तुओं की ओर लौटो।", "Logical Investigations", listOf("things themselves")),
            Q("Rawls", "Justice is the first virtue of social institutions, as truth is of systems of thought.", "न्याय सामाजिक संस्थाओं का पहला गुण है, जैसे सत्य विचार-प्रणालियों का।", "A Theory of Justice", listOf("Justice", "virtue", "truth")),
            Q("Hannah Arendt", "The banality of evil.", "बुराई की साधारणता (सोचने से इनकार ही अनर्थ बन जाता है)।", "Eichmann in Jerusalem", listOf("banality", "evil")),
            Q("Rousseau", "Man is born free, and everywhere he is in chains.", "मनुष्य स्वतंत्र पैदा होता है, पर हर जगह बेड़ियों में है।", "The Social Contract", listOf("free", "chains")),
            Q("Hobbes", "The life of man: solitary, poor, nasty, brutish, and short.", "मनुष्य का जीवन (प्राकृतिक अवस्था में): एकाकी, दरिद्र, घृणित, पाशविक और अल्प।", "Leviathan", listOf("solitary", "brutish", "short")),
            Q("Francis Bacon", "Knowledge is power.", "ज्ञान ही शक्ति है।", "Meditationes Sacrae", listOf("Knowledge", "power")),
            Q("Pascal", "The heart has its reasons which reason does not know.", "हृदय के अपने कारण हैं जिन्हें बुद्धि नहीं जानती।", "Pensées", listOf("heart", "reason")),
            Q("Augustine", "Our heart is restless until it rests in You.", "हमारा हृदय तब तक बेचैन है जब तक वह तुझमें विश्राम न पाए।", "Confessions", listOf("restless")),
            Q("Epictetus", "Men are disturbed not by things, but by the views they take of them.", "मनुष्य वस्तुओं से नहीं, उनके बारे में अपने विचारों से विचलित होते हैं।", "Enchiridion 5", listOf("disturbed", "views")),
            Q("Marcus Aurelius", "What stands in the way becomes the way.", "जो बाधा है, वही मार्ग बन जाती है।", "Meditations 5.20", listOf("way")),
            Q("Seneca", "It is not that we have a short time to live, but that we waste much of it.", "हमारे पास समय कम नहीं है, हम उसका बहुत हिस्सा गँवाते हैं।", "On the Shortness of Life", listOf("short", "waste")),
            Q("Confucius", "Do not do to others what you do not want done to yourself.", "दूसरों के साथ वह मत करो जो तुम अपने साथ नहीं चाहते।", "Analects 15.23", listOf("others", "yourself")),
            Q("Lao Tzu", "A journey of a thousand miles begins with a single step.", "हज़ार मील की यात्रा एक कदम से शुरू होती है।", "Tao Te Ching 64", listOf("journey", "single step")),
            Q("Upanishad", "Tat tvam asi: that thou art.", "वह तुम ही हो (जीव और ब्रह्म एक)।", "Chandogya Upanishad 6.8.7", listOf("Tat tvam asi")),
            Q("Upanishad", "Uttishthata jagrata prapya varan nibodhata: arise, awake.", "उठो, जागो और श्रेष्ठ जनों के पास जाकर ज्ञान प्राप्त करो।", "Katha Upanishad 1.3.14", listOf("arise", "awake")),
            Q("Bhagavad Gita", "Karmanyevadhikaraste ma phaleshu kadachana.", "कर्म पर ही तेरा अधिकार है, फल पर कभी नहीं।", "Bhagavad Gita 2.47", listOf("Karmanyevadhikaraste", "phaleshu")),
            Q("Bhagavad Gita", "Yogah karmasu kaushalam: yoga is skill in action.", "योग कर्मों में कुशलता है।", "Bhagavad Gita 2.50", listOf("skill", "action")),
            Q("Bhagavad Gita", "Samatvam yoga uchyate: equanimity is called yoga.", "समत्व ही योग कहलाता है।", "Bhagavad Gita 2.48", listOf("equanimity", "Samatvam")),
            Q("Patanjali", "Yogas chitta-vritti-nirodhah.", "योग चित्त की वृत्तियों का निरोध है।", "Yoga Sutra 1.2", listOf("chitta", "vritti", "nirodhah")),
            Q("Shankaracharya", "Brahma satyam jagan mithya, jivo brahmaiva naparah.", "ब्रह्म सत्य है, जगत् मिथ्या है, जीव ब्रह्म ही है, भिन्न नहीं।", "Brahmajnanavali", listOf("Brahma satyam", "mithya")),
            Q("Buddha", "Atta dipa viharatha: be a lamp unto yourselves.", "अप्प दीपो भव: स्वयं अपने दीपक बनो।", "Mahaparinibbana Sutta", listOf("lamp")),
            Q("Buddha", "Mind is the forerunner of all actions.", "मन सभी धर्मों (कर्मों) का अग्रदूत है।", "Dhammapada 1", listOf("Mind", "forerunner")),
            Q("Buddha", "Sabbe sankhara anicca: all conditioned things are impermanent.", "सभी संस्कार (संस्कृत वस्तुएँ) अनित्य हैं।", "Dhammapada 277", listOf("anicca", "impermanent")),
            Q("Nagarjuna", "Whatever is dependently arisen, that is emptiness.", "जो प्रतीत्यसमुत्पन्न है, वही शून्यता है।", "Mulamadhyamakakarika 24.18", listOf("dependently arisen", "emptiness")),
            Q("Mahavira", "Parasparopagraho jivanam: souls render service to one another.", "जीव परस्पर एक-दूसरे के उपकारक हैं।", "Tattvartha Sutra 5.21", listOf("service", "Parasparopagraho")),
            Q("Gandhi", "Truth is God.", "सत्य ही ईश्वर है।", "Gandhi's own formulation", listOf("Truth", "God")),
            Q("Gandhi", "I have nothing new to teach the world. Truth and non-violence are as old as the hills.", "मेरे पास दुनिया को सिखाने के लिए कुछ नया नहीं: सत्य और अहिंसा पहाड़ों जितने पुराने हैं।", "Harijan, 1936", listOf("Truth", "non-violence")),
            Q("Vivekananda", "Each soul is potentially divine.", "प्रत्येक आत्मा संभावित रूप से दिव्य है।", "Karma-Yoga (Complete Works)", listOf("soul", "divine")),
            Q("Tagore", "Where the mind is without fear and the head is held high.", "जहाँ मन भयमुक्त हो और मस्तक ऊँचा।", "Gitanjali 35", listOf("fear", "high")),
            Q("Ambedkar", "Educate, agitate, organize.", "शिक्षित बनो, आंदोलन करो, संगठित रहो।", "Ambedkar's motto", listOf("Educate", "agitate", "organize")),
            Q("Ambedkar", "Democracy is primarily a mode of associated living.", "लोकतंत्र मूलतः साथ-साथ जीने का एक तरीका है।", "Annihilation of Caste", listOf("Democracy", "associated living")),
        )

    private var qShift = 0

    /** Two quotes a day: morning slot until 15:00, evening slot after. [qShift] only previews more. */
    fun slot(): Int {
        val hr = java.time.LocalDateTime.now(Repo.zone()).hour
        return Targets.today().toEpochDay().toInt() * 2 + (if (hr >= 15) 1 else 0)
    }

    fun quoteOfDay(): Q = quotes[Math.floorMod(slot() + qShift, quotes.size)]

    private fun colorFor(who: String): Int = T.palette[Math.floorMod(who.hashCode(), T.palette.size)]

    /** Quote text with each keyword in its own colour and bold. */
    fun colored(q: Q): CharSequence {
        val sb = android.text.SpannableStringBuilder("\u201C" + q.text + "\u201D")
        q.kw.forEachIndexed { i, w ->
            val col = T.textAccent(T.palette[Math.floorMod(i * 3 + 1 + Math.floorMod(q.who.hashCode(), 5), T.palette.size)])
            var from = 0
            while (true) {
                val at = sb.toString().indexOf(w, from, ignoreCase = true)
                if (at < 0) break
                sb.setSpan(android.text.style.ForegroundColorSpan(col), at, at + w.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), at, at + w.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                from = at + w.length
            }
        }
        return sb
    }

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

    /** AI note for Philosophy optional: meaning, example and where to use it. Cached per quote. */
    fun explain(c: Activity, q: Q) {
        val key = "qai_" + (q.who + q.text).hashCode()
        val cached = Store.s(key, "")
        if (cached.isNotBlank()) {
            Dlg.info(c, q.who, cached)
            return
        }
        if (!Ai.ready()) {
            c.toast("Add your AI key in Settings > AI")
            return
        }
        c.toast("Asking AI...")
        Thread {
            try {
                val out = Ai.oneShot(c, "Philosophy optional quote: \"${q.text}\" by ${q.who} (${q.src}). Give: 1) meaning, 2) one real-life example, 3) one Mains answer use. Hinglish, 4 short lines.")
                Store.put(key, out)
                c.runOnUiThread { Dlg.info(c, q.who, out) }
            } catch (e: Exception) {
                c.runOnUiThread { c.toast("AI failed: " + (e.message ?: "error")) }
            }
        }.start()
    }

    fun card(h: Host): View {
        val c = h.act
        val p = today()
        val col = T.palette[all.indexOf(p) % T.palette.size]
        val card = Card(c)
        card.addView(c.cardHeader("book", "Philosophy quote of the day", col))
        val qBox = c.vbox()
        card.addView(qBox, lp(-1, -2, 6))
        fun drawQuote() {
            qBox.removeAllViews()
            val q = quoteOfDay()
            val qc = colorFor(q.who)
            val panel = c.vbox()
            panel.padDp(16, 14, 16, 14)
            panel.background =
                android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    intArrayOf(T.withAlpha(qc, 0.26f), T.withAlpha(qc, 0.07f)),
                ).apply {
                    cornerRadius = T.dp(18).toFloat()
                    setStroke(T.dp(1), T.withAlpha(qc, 0.35f))
                }
            panel.elevation = T.dp(5).toFloat()
            val hr = java.time.LocalDateTime.now(Repo.zone()).hour
            panel.addView(c.text(if (qShift != 0) "More quotes" else if (hr >= 15) "Evening quote" else "Morning quote", 11f, 3, T.textAccent(qc)), lp(-2, -2, 4))
            panel.addView(c.text("", 17f, 3, T.text).apply { text = colored(q) }, lp(-1, -2, 2))
            panel.addView(c.text(q.hi, 14f, 2, T.text), lp(-1, -2, 8))
            panel.addView(c.text("- ${q.who}  |  ${q.src}", 11.5f, 1, T.text3), lp(-2, -2, 6))
            val tp = all.firstOrNull { q.who.startsWith(it.name) || it.name.startsWith(q.who) }
            if (tp != null) panel.tap { detail(c, tp) }
            qBox.addView(panel, lp(-1, -2, 4))
            qBox.addView(
                c.hbox().apply {
                    addView(c.pill("Next", "refresh", false, qc) { qShift++; drawQuote() })
                    addView(c.pill("Explain (AI)", "sparkle", true, qc) { explain(c, q) }, lpm(-2, -2, 8, 0, 0, 0))
                    addView(
                        c.pill("Copy", "note", false, qc) {
                            val cm = c.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("quote", "\"${q.text}\" - ${q.who} (${q.src})"))
                            c.toast("Copied")
                        },
                        lpm(-2, -2, 8, 0, 0, 0),
                    )
                },
                lp(-2, -2, 8),
            )
            qBox.addView(c.text("${quotes.size} quotes, changes morning and evening${if (tp != null) ", tap quote for thinker" else ""}", 10.5f, 0, T.text3), lp(-2, -2, 4))
        }
        drawQuote()
        card.addView(c.text("Thinker of the day: " + p.name, 15f, 3, T.textAccent(col)), lp(-2, -2, 12))
        card.addView(body(c, p, col), lp(-1, -2, 4))
        val row = c.hbox()
        row.addView(c.pill("All thinkers", "grid", false, col) { list(c) })
        card.addView(row, lp(-2, -2, 10))
        return card
    }
}
