package com.zemlianikin.flexiblesearch.plugins.converter.calc.engine

// Серый ящик: словарь лексера. Данные, а не код: RU и EN слиты, склонения
// перечислены явно.

import com.zemlianikin.flexiblesearch.plugins.converter.core.CurrencyCode
import com.zemlianikin.flexiblesearch.plugins.converter.core.Decimal
import com.zemlianikin.flexiblesearch.plugins.converter.core.Num

/**
 * Слово (в нижнем регистре) → что оно значит. Слова разных категорий и
 * языков не пересекаются: конфликт ловится при сборке. Совпадение только
 * целым словом, регистронезависимо.
 */
class Lexicon private constructor(private val words: Map<String, Meaning>) {

    sealed interface Meaning {
        data class Currency(val code: CurrencyCode) : Meaning

        /**
         * Слово, за которым стоит несколько валют: `kr`, `песо`. Разбор
         * запрещён.
         */
        data class Ambiguous(val options: List<CurrencyCode>) : Meaning

        data class Scale(val factor: Num) : Meaning

        data class Operator(val operation: Operation) : Meaning

        data object To : Meaning

        data object Of : Meaning
    }

    /**
     * Знаки-валюты (`€`, `₽`), не буквы: лексер режет их отдельным словом,
     * даже вплотную к соседям.
     */
    val symbols: Set<Char> =
        words.keys
            .filter { it.length == 1 && !isWordChar(it[0]) }
            .map { it[0] }
            .toSet()

    // Настоящие префиксы слов: по ним слово у конца ввода можно дописать
    // (`us` → `usd`).
    private val prefixes: Set<String> =
        words.keys
            .flatMap { word -> (1 until word.length).map { word.take(it) } }
            .toSet()

    fun meaning(word: String): Meaning? = words[word.lowercase()]

    /** Слово — собственный префикс какого-то слова словаря. */
    fun canExtend(word: String): Boolean = word.lowercase() in prefixes

    /**
     * Валюты, у которых есть слово, начинающееся с [prefix] (`us` → USD).
     * Неоднозначное слово даёт все свои варианты. Порядок не значим:
     * сортировку (например, по frecency) делает вызывающий.
     */
    fun currenciesWithPrefix(prefix: String): List<CurrencyCode> {
        val start = prefix.lowercase()
        return words
            .filterKeys { it.startsWith(start) }
            .values
            .flatMap { meaning ->
                when (meaning) {
                    is Meaning.Currency -> listOf(meaning.code)
                    is Meaning.Ambiguous -> meaning.options
                    else -> emptyList()
                }
            }
            .distinct()
    }

    class Builder {
        private val words = LinkedHashMap<String, Meaning>()

        fun currency(code: String, vararg names: String) {
            val meaning = Meaning.Currency(CurrencyCode(code))
            add(code.lowercase(), meaning)
            names.forEach { add(it, meaning) }
        }

        fun ambiguous(options: List<String>, vararg names: String) {
            val meaning = Meaning.Ambiguous(options.map(::CurrencyCode))
            names.forEach { add(it, meaning) }
        }

        fun scale(factor: Long, vararg names: String) {
            val meaning = Meaning.Scale(Num(Decimal.of(factor)))
            names.forEach { add(it, meaning) }
        }

        fun operator(operation: Operation, vararg names: String) {
            names.forEach { add(it, Meaning.Operator(operation)) }
        }

        fun to(vararg names: String) = names.forEach { add(it, Meaning.To) }

        fun of(vararg names: String) = names.forEach { add(it, Meaning.Of) }

        private fun add(word: String, meaning: Meaning) {
            require(word == word.lowercase()) {
                "Слово '$word' должно быть в нижнем регистре"
            }
            require(word.length == 1 || word.all(::isWordChar)) {
                "Слово '$word' должно состоять из букв и '$' " +
                    "(символы валют — по одному знаку)"
            }
            // Кириллическая «с» вместо латинской и подобные опечатки не
            // найти глазами.
            require(
                word
                    .filter { it.isLetter() }
                    .map(::alphabet)
                    .toSet()
                    .size <= 1
            ) {
                "Слово '$word' смешивает алфавиты"
            }
            val old = words[word]
            require(old == null) { "Слово '$word' занято: $old и $meaning" }
            words[word] = meaning
        }

        internal fun build(): Lexicon {
            val known =
                words.values
                    .filterIsInstance<Meaning.Currency>()
                    .map { it.code }
                    .toSet()
            for ((word, meaning) in words) {
                if (meaning is Meaning.Ambiguous) {
                    val unknown = meaning.options - known
                    require(unknown.isEmpty()) {
                        "Слово '$word': валют $unknown нет в словаре"
                    }
                }
            }
            return Lexicon(words.toMap())
        }
    }

    companion object {
        /** Буква слова: буквы и `$` (для `C$`, `A$`, `US$`). */
        fun isWordChar(c: Char): Boolean = c.isLetter() || c == '$'

        /**
         * Алфавит буквы, грубо по диапазонам Unicode: хватает, чтобы
         * поймать смесь латиницы с кириллицей.
         */
        private fun alphabet(c: Char): Int =
            when (c) {
                in '\u0041'..'\u024F' -> 0 // латиница с диакритикой
                in '\u0370'..'\u03FF' -> 1 // греческий
                in '\u0400'..'\u052F' -> 2 // кириллица
                else -> 3
            }

        fun build(block: Builder.() -> Unit): Lexicon =
            Builder().apply(block).build()

        val Default: Lexicon = build(Builder::defaultWords)
    }
}

// Неоднозначные слова заведены не по русским названиям, а по смыслу: если у
// слова в справочнике
// больше одной валюты, это ошибка «уточните». Единственное исключение из
// правила — `$` и слово
// «доллар» с формами: всегда USD, остальные доллары только с префиксом.
private fun Lexicon.Builder.defaultWords() {
    operator(Operation.Multiply, "x", "х")
    to("to", "in", "в", "на")
    of("of", "от")
    scale(1_000, "k", "к", "тыс", "тысяч", "thousand")
    scale(
        1_000_000,
        "m",
        "м",
        "млн",
        "миллион",
        "миллиона",
        "миллионов",
        "million",
    )

    currency(
        "USD",
        "\$",
        "us\$",
        "доллар",
        "доллара",
        "доллару",
        "долларом",
        "долларе",
        "доллары",
        "долларов",
        "долларам",
        "долларами",
        "долларах",
        "долл",
        "бакс",
        "бакса",
        "баксу",
        "баксом",
        "баксе",
        "баксы",
        "баксов",
        "баксам",
        "баксами",
        "баксах",
        "dollar",
        "dollars",
        "buck",
        "bucks",
    )
    currency("EUR", "€", "евро")
    currency(
        "RUB",
        "₽",
        "р",
        "руб",
        "рубль",
        "рубля",
        "рублю",
        "рублём",
        "рублем",
        "рубле",
        "рубли",
        "рублей",
        "рублям",
        "рублями",
        "рублях",
        "ruble",
        "rubles",
        "rouble",
        "roubles",
    )
    currency(
        "GBP",
        "£",
        "фунт",
        "фунта",
        "фунту",
        "фунтом",
        "фунте",
        "фунты",
        "фунтов",
        "фунтам",
        "фунтами",
        "фунтах",
        "pound",
        "pounds",
    )
    currency(
        "JPY",
        "иена",
        "иены",
        "иене",
        "иену",
        "иеной",
        "иен",
        "иенам",
        "иенами",
        "иенах",
        "йена",
        "йены",
        "йене",
        "йену",
        "йеной",
        "йен",
        "йенам",
        "йенами",
        "йенах",
        "yen",
    )
    currency(
        "CNY",
        "rmb",
        "renminbi",
        "юань",
        "юаня",
        "юаню",
        "юанем",
        "юане",
        "юани",
        "юаней",
        "юаням",
        "юанями",
        "юанях",
        "yuan",
    )
    currency("CHF")
    currency("CAD", "c\$", "ca\$")
    currency("AUD", "a\$", "au\$")
    currency("NZD", "nz\$")
    currency("HKD", "hk\$")
    currency("SGD", "s\$")
    currency(
        "THB",
        "฿",
        "бат",
        "бата",
        "бату",
        "батом",
        "бате",
        "баты",
        "батов",
        "батам",
        "батами",
        "батах",
        "baht",
        "bahts",
    )
    currency(
        "TRY",
        "₺",
        "tl",
        "лира",
        "лиры",
        "лире",
        "лиру",
        "лирой",
        "лир",
        "лирам",
        "лирами",
        "лирах",
        "lira",
        "liras",
    )
    currency("KZT", "₸", "тг", "тенге", "tenge")
    currency(
        "UAH",
        "₴",
        "грн",
        "гривна",
        "гривны",
        "гривне",
        "гривну",
        "гривной",
        "гривен",
        "гривнам",
        "гривнами",
        "гривнах",
        "hryvnia",
        "hryvnias",
    )
    currency("BYN")
    currency("GEL", "₾", "лари", "lari")
    currency(
        "AMD",
        "֏",
        "драм",
        "драма",
        "драму",
        "драмом",
        "драме",
        "драмы",
        "драмов",
        "драмам",
        "драмами",
        "драмах",
        "dram",
        "drams",
    )
    currency("AED")
    currency("MAD")
    currency(
        "PLN",
        "zł",
        "zl",
        "злотый",
        "злотого",
        "злотому",
        "злотым",
        "злотом",
        "злотые",
        "злотых",
        "злотыми",
        "zloty",
        "zlotys",
    )
    currency(
        "CZK",
        "kč",
        "kc",
        "koruna",
        "korun",
        "корун",
        "корона",
        "короны",
        "коруна",
        "коруны",
    )
    currency("SEK")
    currency("NOK")
    currency("DKK")
    currency("ISK")
    currency("KRW", "₩", "вона", "воны", "вон", "won")
    currency("INR", "₹")
    currency("IDR", "rp")
    currency("PKR")
    currency("VND", "₫", "донг", "донга", "донгов", "dong")
    currency("MXN")
    currency("ARS")
    currency("CLP")
    currency("COP")
    currency(
        "BRL",
        "r\$",
        "реал",
        "реала",
        "реалу",
        "реалом",
        "реале",
        "реалы",
        "реалов",
        "реалам",
        "реалами",
        "реалах",
        "real",
        "reais",
    )
    currency(
        "ILS",
        "₪",
        "шекель",
        "шекеля",
        "шекелей",
        "шекели",
        "shekel",
        "shekels",
    )
    currency("ZAR", "рэнд", "рэнда", "рэндов", "rand")
    currency("HUF", "форинт", "форинта", "форинтов", "forint")
    currency("RON")
    currency("MDL")
    currency("BGN", "лв", "лев", "лева", "левов", "lev", "leva")
    currency("MYR", "rm", "ринггит", "ринггита", "ринггитов", "ringgit")
    currency("PHP", "₱")
    currency("AZN", "₼")
    currency("UZS", "сум", "сума", "сумов")
    currency("KGS", "сом", "сома", "сомов")
    currency("RSD")
    currency("KWD")
    currency("BHD")
    currency("JOD")
    currency("EGP")
    currency("XOF")
    currency("XAF")

    ambiguous(listOf("JPY", "CNY"), "¥", "￥")
    ambiguous(listOf("SEK", "NOK", "DKK"), "kr")
    ambiguous(
        listOf("SEK", "NOK", "DKK", "CZK", "ISK"),
        "крона",
        "кроны",
        "кроне",
        "крону",
        "кроной",
        "крон",
        "кронам",
        "кронами",
        "кронах",
        "krona",
        "kronor",
        "krone",
        "kroner",
        "crown",
        "crowns",
    )
    ambiguous(listOf("MXN", "ARS", "CLP", "COP"), "песо", "peso", "pesos")
    ambiguous(
        listOf("RSD", "KWD", "BHD", "JOD"),
        "динар",
        "динара",
        "динару",
        "динаром",
        "динаре",
        "динары",
        "динаров",
        "dinar",
        "dinars",
    )
    ambiguous(
        listOf("CHF", "XOF", "XAF"),
        "франк",
        "франка",
        "франку",
        "франком",
        "франке",
        "франки",
        "франков",
        "franc",
        "francs",
    )
    ambiguous(
        listOf("INR", "IDR", "PKR"),
        "рупия",
        "рупии",
        "рупию",
        "рупией",
        "рупий",
        "рупиям",
        "рупиями",
        "рупиях",
        "rupee",
        "rupees",
    )
    ambiguous(listOf("RON", "MDL"), "лей", "лея", "leu", "lei")
    ambiguous(
        listOf("AED", "MAD"),
        "дирхам",
        "дирхама",
        "дирхаму",
        "дирхамом",
        "дирхаме",
        "дирхамы",
        "дирхамов",
        "dirham",
        "dirhams",
    )
}
