package com.tindi.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tindi.api.service.GroundingCheck;
import com.tindi.api.service.GroundingCheck.Kind;
import com.tindi.api.service.GroundingCheck.Report;
import com.tindi.api.service.GroundingCheck.Source;
import com.tindi.api.service.GroundingCheck.Verdict;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GroundingCheckTest {
    private static final String ENGLISH = """
            Ingredients:
            - 250 g paneer, cubed
            - 2 tbsp butter
            - 3 tomatoes, pureed
            - 1 tsp cumin seeds
            - 1½ cups water
            - salt to taste

            Method:
            1. Melt the butter in a pan.
            2. Add the cumin seeds and let them splutter.
            3. Pour in the tomato puree and simmer for 8 minutes.
            4. Add water, salt and paneer. Cook for 3 minutes.
            """;

    private static GroundingCheck of(String text) {
        return new GroundingCheck(List.of(new Source(Kind.DESCRIPTION, text)));
    }

    private static Verdict verdict(String source, String item) {
        return of(source).check(item).verdict();
    }

    private static void assertAll(Verdict expected, String source, String... items) {
        for (String item : items) assertEquals(expected, verdict(source, item), item);
    }

    @Test
    void exactCopyIsVerified() {
        assertAll(Verdict.VERIFIED, ENGLISH, "250 g paneer, cubed", "2 tbsp butter", "3 tomatoes, pureed",
                "1 tsp cumin seeds", "1½ cups water", "salt to taste", "Melt the butter in a pan.",
                "Pour in the tomato puree and simmer for 8 minutes.", "Cook for 3 minutes.");
    }

    @Test
    void lightRewordingIsVerified() {
        assertAll(Verdict.VERIFIED, ENGLISH,
                "Paneer, cubed - 250g", // reordered, no space
                "Butter - 2 tbsp",
                "1 1/2 cups water", // the same fraction written out
                "Melt butter in the pan",
                "Add cumin seed and let it splutter",
                "Add water, salt and paneer",
                "Simmer the tomato puree for 8 minutes");
    }

    @Test
    void partlyMatchedWordingIsPartial() {
        assertAll(Verdict.PARTIAL, ENGLISH,
                "Gently melt the butter in a pan", // one added word
                "Melt the buttar in a pan"); // spelling differs
    }

    @Test
    void inventedIngredientsAndStepsAreNotFound() {
        assertAll(Verdict.NOT_FOUND, ENGLISH, "saffron", "2 tbsp fresh cream", "1 tsp garam masala",
                "Garnish with coriander leaves.", "Refrigerate overnight before serving.", "🌿", "");
    }

    @Test
    void inventedOrChangedQuantitiesAreUnverified() {
        assertAll(Verdict.UNVERIFIED_QUANTITY, ENGLISH,
                "500 g paneer", // number not in the source at all
                "3 tbsp butter", // number belongs to the tomatoes
                "2 tsp butter", // unit changed
                "250 kg paneer",
                "2 tomatoes, pureed",
                "1 cup water", // 1½ became 1
                "a pinch of salt",
                "two tbsp butter", // not as written
                "Cook for 8 minutes and add paneer for 15 minutes",
                "Cook the paneer for 2 minutes");
    }

    @Test
    void swappedQuantitiesOnOneLineAreCaught() {
        String source = "You need 2 eggs, 3 onions and 1 tomato.";
        assertAll(Verdict.VERIFIED, source, "2 eggs", "3 onions", "1 tomato");
        assertAll(Verdict.UNVERIFIED_QUANTITY, source, "3 eggs", "1 onions", "2 tomato");
    }

    @Test
    void listNumberingIsNotAQuantity() {
        String source = "1. Add eggs\n2) Add milk\nStep 3: Whisk";
        assertAll(Verdict.VERIFIED, source, "Add eggs", "1. Add eggs", "Whisk");
        assertAll(Verdict.UNVERIFIED_QUANTITY, source, "Add 1 egg", "Add 3 eggs");
    }

    @Test
    void quantitiesOnTheirOwnLinesAndRangesWork() {
        String source = "Rice\n2 cups\nCurry leaves 10-12\nCloves: 4";
        assertAll(Verdict.VERIFIED, source, "2 cups rice", "10-12 curry leaves", "4 cloves");
        assertAll(Verdict.UNVERIFIED_QUANTITY, source, "10-15 curry leaves", "9 cloves");
    }

    @Test
    void mixedHindiAndEnglish() {
        String source = """
                Samagri (Ingredients):
                1 cup toor dal
                2 चम्मच घी
                आधा चम्मच हल्दी
                ४ lahsun ki kaliyan

                Vidhi:
                Dal ko 4 seeti tak pressure cook karein.
                घी गरम करके जीरा और लहसुन का तड़का बनाएं।
                """;
        assertAll(Verdict.VERIFIED, source, "1 cup toor dal", "2 चम्मच घी", "आधा चम्मच हल्दी", "4 lahsun ki kaliyan",
                "Dal ko 4 seeti tak pressure cook karein", "घी गरम करके जीरा और लहसुन का तड़का बनाएं");
        assertAll(Verdict.UNVERIFIED_QUANTITY, source, "3 चम्मच घी", "Dal ko 6 seeti tak pressure cook karein");
        // A translation is not the source text.
        assertAll(Verdict.NOT_FOUND, source, "Heat clarified fat with cumin and garlic", "turmeric powder");
    }

    @Test
    void emojiHeadingsBulletsAndKeycapNumbers() {
        String source = """
                🍛 Chicken Curry 🍛
                🧂 INGREDIENTS 🧂
                ✅ 500 g chicken
                ✅ 2️⃣ onions
                👩‍🍳 DIRECTIONS 👩‍🍳
                1️⃣ Fry the onions until brown 🔥
                2️⃣ Add the chicken ✨
                """;
        assertAll(Verdict.VERIFIED, source, "500 g chicken", "Fry the onions until brown", "Add the chicken 🍗");
        // Keycap digits are decoration, so they cannot vouch for a quantity.
        assertAll(Verdict.UNVERIFIED_QUANTITY, source, "2 onions");
    }

    @Test
    void wordsAreMatchedWithinOneSourceAndReportedByKind() {
        GroundingCheck check = new GroundingCheck(List.of(
                new Source(Kind.TITLE, "Jeera rice"),
                new Source(Kind.DESCRIPTION, "Full recipe below"),
                new Source(Kind.COMMENT, "2 cups basmati\n1 tbsp ghee")));
        assertEquals(Set.of(Kind.COMMENT), check.check("1 tbsp ghee").foundIn());
        assertEquals(Set.of(Kind.TITLE), check.check("Jeera rice").foundIn());
    }

    @Test
    void reviewDropsKeepsAndWarns() {
        Report r = of(ENGLISH).review(List.of("2 tbsp butter", "saffron", "500 g paneer", "  "),
                List.of("Gently melt the butter in a pan", "Garnish with coriander."));
        assertEquals(List.of("2 tbsp butter", "500 g paneer"), r.ingredients());
        assertEquals(List.of("Gently melt the butter in a pan"), r.steps());
        assertEquals(5, r.total());
        assertEquals(2, r.dropped());
        assertEquals(3, r.failed()); // two dropped and one invented quantity
        assertTrue(r.majorityFailed());
        assertEquals(List.of(
                "Check ingredient 2: a number or quantity does not match the source text.",
                "Removed 1 ingredient that is not in the source text.",
                "Check step 1: it is worded differently from the source text.",
                "Removed 1 step that is not in the source text."), r.warnings());
    }

    @Test
    void exactlyHalfFailingIsNotAMajority() {
        Report r = of(ENGLISH).review(List.of("2 tbsp butter", "saffron"), List.of());
        assertFalse(r.majorityFailed());
        assertFalse(of(ENGLISH).review(List.of(), List.of()).majorityFailed());
    }
}
