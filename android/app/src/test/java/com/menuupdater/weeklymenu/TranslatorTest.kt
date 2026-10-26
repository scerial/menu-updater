package com.menuupdater.weeklymenu

import com.menuupdater.weeklymenu.domain.TranslationData
import com.menuupdater.weeklymenu.domain.Translator
import org.junit.Assert.assertEquals
import org.junit.Test

/** The translator's rules, against a small hand-written dictionary. */
class TranslatorTest {
    private val translator = Translator(
        TranslationData(
            dishes = mapOf("콩나물국" to "Bean Sprout Soup", "김치" to "Kimchi", "딸기잼" to "Strawberry Jam", "샐러드" to "Salad", "토스트" to "Toast"),
            aliases = mapOf("공나물국" to "콩나물국"),
            glossary = mapOf(
                "돈육" to "Pork", "김치" to "Kimchi", "볶음" to "Stir-fry", "고사리" to "Bracken",
                "무침" to "^Seasoned", "볶음밥" to "Fried Rice", "새우" to "Shrimp",
            ),
            places = mapOf("향설1관" to "Hyangseol Hall 1"),
            meals = mapOf("중식" to "Lunch"),
            weekdays = mapOf("화" to "Tue"),
        ),
    )

    @Test
    fun dictionaryHit() {
        assertEquals("Bean Sprout Soup", translator.dish("콩나물국"))
    }

    @Test
    fun aliasMapsAMisreadToTheDictionary() {
        assertEquals("Bean Sprout Soup", translator.dish("공나물국"))
    }

    @Test
    fun spacesAreIgnoredForADictionaryHit() {
        assertEquals("Strawberry Jam", translator.dish("딸기 잼"))
    }

    @Test
    fun guessesWordByWordInKoreanOrder() {
        assertEquals("Pork Kimchi Stir-fry", translator.dish("돈육김치볶음"))
    }

    @Test
    fun caretPartsMoveToTheFront() {
        assertEquals("Seasoned Bracken", translator.dish("고사리무침"))
    }

    @Test
    fun theFewestPartsWin() {
        // 새우 + 볶음밥 (2 parts), not 새우 + 볶음 + 밥.
        assertEquals("Shrimp Fried Rice", translator.dish("새우볶음밥"))
    }

    @Test
    fun anUncoveredPartKeepsTheKorean() {
        assertEquals("교자민두", translator.dish("교자민두"))
        assertEquals(listOf(Translator.Source.NONE), translator.sources("교자민두"))
    }

    @Test
    fun compoundItemsKeepTheirSeparators() {
        assertEquals("Kimchi / Salad / 후식차", translator.dish("김치/샐러드/후식차"))
        assertEquals("Bean Sprout Soup / Kimchi", translator.dish("공나물국 / 김치"))
    }

    @Test
    fun reportsWhereEachDishCameFrom() {
        assertEquals(
            listOf(Translator.Source.DICTIONARY, Translator.Source.GUESS, Translator.Source.NONE),
            translator.sources("김치/돈육김치볶음/후식차"),
        )
    }

    @Test
    fun dishesRunTogetherAreJoinedWithAnd() {
        assertEquals("Kimchi & Salad", translator.dish("김치샐러드"))
        assertEquals("Bean Sprout Soup & Kimchi", translator.dish("콩나물국 김치"))
    }

    @Test
    fun plusAndTildeAlsoSeparateDishes() {
        assertEquals("Kimchi + Salad", translator.dish("김치+샐러드"))
        assertEquals("Kimchi + Salad", translator.dish("김치*샐러드"))
        assertEquals("Toast & Strawberry Jam", translator.dish("토스트&딸기잼"))
        assertEquals("Kimchi ~ Salad", translator.dish("김치~샐러드"))
    }

    @Test
    fun placesMealsAndWeekdays() {
        assertEquals("Hyangseol Hall 1", translator.place("향설1관"))
        assertEquals("Lunch", translator.meal("중식"))
        assertEquals("Tue", translator.weekday("화"))
        assertEquals("교직원식당", translator.place("교직원식당"))
    }

    @Test
    fun anEmptyFileChangesNothing() {
        val empty = Translator(TranslationData())
        assertEquals("돈육김치볶음", empty.dish("돈육김치볶음"))
        assertEquals("중식", empty.meal("중식"))
    }

    @Test
    fun readsJson() {
        val fromJson = Translator.fromJson("""{"version":1,"dishes":{"김치":"Kimchi"},"extra":1}""")
        assertEquals("Kimchi", fromJson.dish("김치"))
    }
}
