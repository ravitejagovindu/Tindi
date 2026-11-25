package com.tindi.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tindi.api.service.RecipeSplitter;
import com.tindi.api.service.RecipeSplitter.Confidence;
import com.tindi.api.service.RecipeSplitter.FoundIn;
import com.tindi.api.service.RecipeSplitter.Result;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecipeSplitterTest {
    private final RecipeSplitter splitter = new RecipeSplitter();

    private static final String ENGLISH = """
            Restaurant style paneer butter masala that tastes better than takeout!

            Ingredients:
            - 250 g paneer, cubed
            - 2 tbsp butter
            - 3 tomatoes, pureed
            - 1 onion, chopped
            - 1/2 cup cream

            Method:
            1. Melt the butter in a pan.
            2. Add the onion and cook until golden.
            3. Pour in the tomato puree and simmer for 8 minutes.
            4. Add cream and paneer. Cook 3 minutes.

            Follow me on Instagram @tindikitchen
            Subscribe for more recipes every week!
            """;

    @Test
    void englishDescriptionWithBulletsAndNumbers() {
        Result r = splitter.split(ENGLISH, List.of());
        assertEquals(List.of("250 g paneer, cubed", "2 tbsp butter", "3 tomatoes, pureed", "1 onion, chopped", "1/2 cup cream"),
                r.ingredients());
        assertEquals(4, r.steps().size());
        assertEquals("Melt the butter in a pan.", r.steps().get(0));
        assertEquals(Confidence.HIGH, r.confidence());
        assertEquals(FoundIn.DESCRIPTION, r.foundIn());
    }

    @Test
    void hindiEnglishMixedHeadings() {
        String text = """
                Aaj banate hain easy dal tadka!

                Samagri (Ingredients):
                1 cup toor dal
                2 tbsp ghee
                1 tsp jeera
                4 lahsun ki kaliyan

                Vidhi (Method):
                Dal ko 4 seeti tak pressure cook karein.
                Ghee garam karke jeera aur lahsun ka tadka banayein.
                Tadka dal mein daalkar mix karein.
                """;
        Result r = splitter.split(text, List.of());
        assertEquals(4, r.ingredients().size());
        assertEquals("1 cup toor dal", r.ingredients().get(0));
        assertEquals(3, r.steps().size());
        assertEquals(Confidence.HIGH, r.confidence());
    }

    @Test
    void devanagariHeadings() {
        String text = "सामग्री:\n2 कप चावल\n1 चम्मच नमक\n\nविधि:\nचावल धोकर भिगो दें।\nपानी में नमक डालकर उबालें।";
        Result r = splitter.split(text, List.of());
        assertEquals(List.of("2 कप चावल", "1 चम्मच नमक"), r.ingredients());
        assertEquals(2, r.steps().size());
    }

    @Test
    void emojiHeadingsAndEmojiBullets() {
        String text = """
                🍛 Chicken Curry 🍛

                🧂 INGREDIENTS 🧂
                ✅ 500 g chicken
                ✅ 2 onions
                ✅ 1 tbsp ginger garlic paste

                👩‍🍳 DIRECTIONS 👩‍🍳
                👉 Fry the onions until brown.
                👉 Add the chicken and the paste.
                👉 Cover and cook for 25 minutes.
                """;
        Result r = splitter.split(text, List.of());
        assertEquals(List.of("500 g chicken", "2 onions", "1 tbsp ginger garlic paste"), r.ingredients());
        assertEquals("Fry the onions until brown.", r.steps().get(0));
        assertEquals(3, r.steps().size());
    }

    @Test
    void whatYouNeedAndNumberedStepsWithoutColons() {
        String text = """
                What you need
                2 cups flour
                1 cup milk
                1 egg

                Steps
                1) Whisk everything together.
                2) Rest the batter for 10 minutes.
                3) Cook on a hot pan.
                """;
        Result r = splitter.split(text, List.of());
        assertEquals(List.of("2 cups flour", "1 cup milk", "1 egg"), r.ingredients());
        assertEquals(List.of("Whisk everything together.", "Rest the batter for 10 minutes.", "Cook on a hot pan."), r.steps());
    }

    @Test
    void youWillNeedAndStepNLines() {
        String text = "You will need:\n* 3 eggs\n* salt\nInstructions:\nStep 1: Beat the eggs.\nStep 2: Cook in a pan.";
        Result r = splitter.split(text, List.of());
        assertEquals(List.of("3 eggs", "salt"), r.ingredients());
        assertEquals(List.of("Beat the eggs.", "Cook in a pan."), r.steps());
    }

    @Test
    void inlineItemAfterColonIsKept() {
        Result r = splitter.split("Ingredients: 2 cups rice\n1 tsp salt\nMethod: Boil the rice.\nDrain and serve.", List.of());
        assertEquals(List.of("2 cups rice", "1 tsp salt"), r.ingredients());
        assertEquals(List.of("Boil the rice.", "Drain and serve."), r.steps());
    }

    @Test
    void sectionStopsAtNotesAndAtUnrelatedTextAfterAGap() {
        String text = """
                Ingredients
                - 2 bananas
                - 1 cup oats

                Business enquiries and collabs: write to me.

                Method
                - Mash the bananas.
                - Mix with oats and bake.

                Notes:
                - Do not overbake.
                """;
        Result r = splitter.split(text, List.of());
        assertEquals(List.of("2 bananas", "1 cup oats"), r.ingredients());
        assertEquals(List.of("Mash the bananas.", "Mix with oats and bake."), r.steps());
    }

    @Test
    void subLabelsAreNotItems() {
        String text = "Ingredients\nFor the gravy:\n2 tomatoes\n1 onion\nFor the dough:\n1 cup flour\nMethod\n1. Cook the gravy.\n2. Make the dough.";
        Result r = splitter.split(text, List.of());
        assertEquals(List.of("2 tomatoes", "1 onion", "1 cup flour"), r.ingredients());
    }

    @Test
    void noRecipeAtAll() {
        Result r = splitter.split("Vlog day 12! We went to the beach and had so much fun. Subscribe!", List.of("Great video!", "First!"));
        assertTrue(r.ingredients().isEmpty());
        assertTrue(r.steps().isEmpty());
        assertEquals(Confidence.NONE, r.confidence());
        assertEquals(FoundIn.NONE, r.foundIn());
    }

    @Test
    void wordsLikeStepsInsideProseAreNotHeadings() {
        Result r = splitter.split("Steps to success are hard work and luck. Ingredients matter a lot, said the chef.", List.of());
        assertEquals(Confidence.NONE, r.confidence());
    }

    @Test
    void recipeOnlyInACommentIsFound() {
        String comment = """
                Here is the recipe for everyone asking:

                Ingredients:
                2 cups rice
                1 tsp cumin
                1 tbsp ghee

                Method:
                1. Wash the rice.
                2. Temper cumin in ghee.
                3. Add rice and water, cook 15 minutes.
                """;
        Result r = splitter.split("Quick jeera rice. Link to the full playlist below.",
                List.of("Love this!", comment, "Thanks for sharing"));
        assertEquals(FoundIn.COMMENT, r.foundIn());
        assertEquals(3, r.ingredients().size());
        assertEquals(3, r.steps().size());
        assertEquals(Confidence.HIGH, r.confidence());
    }

    @Test
    void commentWithMoreRecipeLinesBeatsAShortDescription() {
        String desc = "Ingredients:\n- 1 egg\nMethod:\n- Fry it.";
        String comment = "Ingredients:\n- 1 egg\n- 1 tsp butter\n- salt\nMethod:\n- Heat butter.\n- Crack egg.\n- Season and serve.";
        Result r = splitter.split(desc, List.of(comment));
        assertEquals(FoundIn.COMMENT, r.foundIn());
        assertEquals(3, r.ingredients().size());
    }

    @Test
    void descriptionWinsATie() {
        String both = "Ingredients:\n- a\n- b\nMethod:\n- c\n- d";
        assertEquals(FoundIn.DESCRIPTION, splitter.split(both, List.of(both)).foundIn());
    }

    @Test
    void onlyOneSectionGivesLowConfidence() {
        Result r = splitter.split("Ingredients:\n- 2 eggs\n- 1 cup milk", List.of());
        assertEquals(2, r.ingredients().size());
        assertTrue(r.steps().isEmpty());
        assertEquals(Confidence.LOW, r.confidence());
    }

    @Test
    void nullsAndBlanksAreSafe() {
        assertEquals(Confidence.NONE, splitter.split(null, null).confidence());
        assertEquals(Confidence.NONE, splitter.split("   ", List.of("")).confidence());
    }

    @Test
    void quantitiesStartingWithDigitsAreNotStripped() {
        Result r = splitter.split("Ingredients:\n2 cups rice\n1.5 tsp salt\n1/2 cup milk\n½ tsp pepper", List.of());
        assertEquals(List.of("2 cups rice", "1.5 tsp salt", "1/2 cup milk", "½ tsp pepper"), r.ingredients());
    }
}
