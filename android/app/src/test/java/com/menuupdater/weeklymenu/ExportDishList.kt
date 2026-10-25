package com.menuupdater.weeklymenu

import com.menuupdater.weeklymenu.domain.DishCorrector
import com.menuupdater.weeklymenu.domain.DishLexicon
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Lists what `shared/translations/en.json` needs: the canonical dishes in the lexicon (most common
 * first) and the misreads the corrector maps onto them. Not part of the normal run:
 *
 *   $env:EXPORT_DISHES = "1"; .\gradlew.bat :app:testDebugUnitTest --tests "*ExportDishList*"
 *
 * Writes `android/build/dish-export.tsv` (`count<TAB>dish`) and `android/build/dish-aliases.tsv`
 * (`misread<TAB>canonical`).
 */
class ExportDishList {
    @Test
    fun export() {
        assumeTrue("set EXPORT_DISHES=1 to export the dish list", System.getenv("EXPORT_DISHES") == "1")
        val module = File(System.getProperty("user.dir"))
        val text = File(module, "src/main/assets/dish-lexicon.json").readText()
        val lexicon = Json { ignoreUnknownKeys = true }.decodeFromString(DishLexicon.serializer(), text)
        val corrector = DishCorrector.fromJson(text)

        val canonical = mutableMapOf<String, Int>()
        val aliases = sortedMapOf<String, String>()
        for ((name, count) in lexicon.dishes) {
            val fixed = corrector.correct(name)
            if (fixed == name) {
                canonical.merge(name, count, Int::plus)
            } else {
                aliases[name] = fixed
                canonical.merge(fixed, 0, Int::plus)
            }
        }
        for ((from, to) in lexicon.fixes) {
            aliases[from] = to
            canonical.merge(to, 0, Int::plus)
        }

        val out = File(module.parentFile, "build").apply { mkdirs() }
        File(out, "dish-export.tsv").writeText(
            canonical.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .joinToString("\n") { "${it.value}\t${it.key}" } + "\n",
        )
        File(out, "dish-aliases.tsv").writeText(aliases.entries.joinToString("\n") { "${it.key}\t${it.value}" } + "\n")
        println("exported ${canonical.size} dishes and ${aliases.size} aliases")
    }
}
