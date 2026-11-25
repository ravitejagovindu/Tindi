package com.tindi.app.data

import java.util.UUID

object SampleData {
    private fun ing(q: String, i: String) = Ingredient(q, i)

    fun recipes(): List<Recipe> = listOf(
        Recipe(
            id = UUID.randomUUID().toString(),
            name = "Paneer Butter Masala",
            ingredients = listOf(
                ing("250 g", "paneer, cubed"), ing("2 tbsp", "butter"), ing("3", "tomatoes, pureed"),
                ing("1", "onion, chopped"), ing("1/2 cup", "cream"), ing("1 tsp", "garam masala"),
            ),
            steps = listOf(
                "Melt 2 tbsp butter in a pan.",
                "Add the chopped onion and cook until golden.",
                "Pour in the tomato puree and simmer for 8 minutes.",
                "Stir in the cream and garam masala.",
                "Add the paneer and cook for 3 minutes. Serve hot.",
            ),
            categoryIds = listOf("starter-dinner"),
            sourceType = SourceType.YouTube,
            sourceUrl = "https://www.youtube.com/watch?v=tindiSample01",
        ),
        Recipe(
            id = UUID.randomUUID().toString(),
            name = "Masala Oats",
            ingredients = listOf(
                ing("1 cup", "rolled oats"), ing("1", "onion, chopped"), ing("1", "tomato, chopped"),
                ing("1/2 cup", "green peas"), ing("1 tsp", "turmeric"), ing("2 cups", "water"),
            ),
            steps = listOf(
                "Dry roast the oats for 2 minutes and set aside.",
                "Saute the onion and tomato in a little oil.",
                "Add the peas and turmeric and stir well.",
                "Pour in the water and bring it to a boil.",
                "Add the oats and cook for 3 minutes until thick.",
            ),
            categoryIds = listOf("starter-breakfast", "starter-healthy"),
            sourceType = SourceType.YouTube,
            sourceUrl = "https://www.youtube.com/watch?v=tindiSample02",
        ),
        Recipe(
            id = UUID.randomUUID().toString(),
            name = "Sprouts Chaat",
            ingredients = listOf(
                ing("2 cups", "boiled sprouts"), ing("1", "onion, chopped"), ing("1", "tomato, chopped"),
                ing("1 tsp", "chaat masala"), ing("1 tbsp", "lemon juice"), ing("2 tbsp", "coriander, chopped"),
            ),
            steps = listOf(
                "Add the boiled sprouts to a large bowl.",
                "Mix in the onion and tomato.",
                "Sprinkle the chaat masala over the top.",
                "Squeeze in the lemon juice and toss.",
                "Garnish with coriander and serve at once.",
            ),
            categoryIds = listOf("starter-snacks", "starter-protein", "starter-healthy"),
            sourceType = SourceType.YouTube,
            sourceUrl = "https://www.youtube.com/watch?v=tindiSample03",
        ),
        Recipe(
            id = UUID.randomUUID().toString(),
            name = "Dal Tadka",
            ingredients = listOf(
                ing("1 cup", "toor dal"), ing("3 cups", "water"), ing("1 tsp", "turmeric"),
                ing("2 tbsp", "ghee"), ing("1 tsp", "cumin seeds"), ing("4", "garlic cloves, sliced"),
            ),
            steps = listOf(
                "Pressure cook the dal with water and turmeric for 4 whistles.",
                "Mash the cooked dal lightly and keep it warm.",
                "Heat the ghee and crackle the cumin seeds.",
                "Add the garlic and fry until golden.",
                "Pour the tadka over the dal and mix.",
            ),
            categoryIds = listOf("starter-lunch", "starter-dinner"),
            sourceType = SourceType.YouTube,
            sourceUrl = "https://www.youtube.com/watch?v=tindiSample04",
        ),
    )
}
