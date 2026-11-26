package com.tindi.api.service;

import com.tindi.api.domain.Illustration;
import com.tindi.api.dto.RecipeDtos.StepDetailDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Cleans the labels the LLM attaches to each step. They are not recipe text, so they are not grounded against
 * the sources, but they must satisfy the recipe rules and point at the ingredients that survived the check.
 * A bad label is repaired or left empty; it never rejects the answer.
 */
final class StepLabels {
    private StepLabels() {}

    /**
     * @param steps        for each surviving step, the object the model returned for it, or null
     * @param newPositions ingredient position in the model's answer to position in the returned list.
     *                     Ingredients that were removed are absent
     * @return one entry for each step, or null if the model labelled none of them
     */
    static List<StepDetailDto> clean(List<JsonNode> steps, Map<Integer, Integer> newPositions) {
        if (steps.stream().noneMatch(StepLabels::labelled)) return null;
        return steps.stream().map(step -> step == null
                ? new StepDetailDto("", List.of(), Illustration.GENERIC.name())
                : new StepDetailDto(name(step.path("name")), indexes(step.path("ingredientIndexes"), newPositions),
                        Illustration.orGeneric(step.path("illustration").asString("")).name())).toList();
    }

    private static boolean labelled(JsonNode step) {
        return step != null && (step.hasNonNull("name") || step.hasNonNull("ingredientIndexes")
                || step.hasNonNull("illustration"));
    }

    /** A name that is too long keeps its leading whole words. If even the first word is too long, it is dropped. */
    private static String name(JsonNode node) {
        if (!node.isString()) return "";
        String name = node.asString().replaceAll("[\\p{Cc}\\s]+", " ").strip();
        if (name.length() <= StepDetailDto.MAX_NAME) return name;
        // Cutting inside a word could split a letter from its vowel sign in scripts such as Devanagari.
        int space = name.lastIndexOf(' ', StepDetailDto.MAX_NAME);
        return space < 0 ? "" : name.substring(0, space).replaceAll("[\\p{P}\\s]+$", "");
    }

    private static List<Integer> indexes(JsonNode node, Map<Integer, Integer> newPositions) {
        List<Integer> out = new ArrayList<>();
        if (!node.isArray()) return out;
        for (JsonNode item : node) {
            if (!item.isIntegralNumber() || !item.canConvertToInt()) continue;
            Integer moved = newPositions.get(item.asInt());
            if (moved != null && !out.contains(moved)) out.add(moved);
        }
        return out;
    }
}
