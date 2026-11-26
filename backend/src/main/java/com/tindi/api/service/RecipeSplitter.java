package com.tindi.api.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule based splitter. Looks for an "ingredients" section and a "method" section in the video
 * description first and then in each comment, and keeps the text that holds the most recipe-like lines.
 * No LLM, no network.
 */
public class RecipeSplitter {
    public enum Confidence { HIGH, LOW, NONE }

    public enum FoundIn { DESCRIPTION, COMMENT, NONE }

    public record Result(List<String> ingredients, List<String> steps, Confidence confidence, FoundIn foundIn) {
        public static Result none() {
            return new Result(List.of(), List.of(), Confidence.NONE, FoundIn.NONE);
        }
    }

    static final int MAX_ITEMS = 100;
    static final int MAX_INGREDIENT = 120;
    static final int MAX_STEP = 1000;

    private static final String ING_WORDS =
            "ingredients?(?:\\s+list)?|what\\s+you(?:'ll|\\u2019ll|\\s+will)?\\s+need|you(?:'ll|\\u2019ll|\\s+will)\\s+need"
                    + "|things\\s+you(?:'ll|\\u2019ll|\\s+will)?\\s+need|samagri|saamagri|\\u0938\\u093e\\u092e\\u0917\\u094d\\u0930\\u0940";
    private static final String METHOD_WORDS =
            "method|steps?|directions?|instructions?|preparation|procedure|how\\s+to\\s+make(?:\\s+it)?|how\\s+to\\s+cook"
                    + "|vidhi|banane\\s+ki\\s+vidhi|\\u0935\\u093f\\u0927\\u093f|\\u092c\\u0928\\u093e\\u0928\\u0947\\s+\\u0915\\u0940\\s+\\u0935\\u093f\\u0927\\u093f";
    private static final String PREFIX = "(?:the\\s+|cooking\\s+|preparation\\s+|recipe\\s+)?";
    // heading, optional "(Hindi word)" or "for 4 people", optional colon, then whatever follows on the same line
    private static final String TAIL = "\\s*(?:\\([^)]{1,40}\\)|for\\s+[^:]{1,40})?\\s*([:\\uff1a\\-\\u2013\\u2014])?\\s*(.*)$";
    private static final Pattern ING_HEAD = Pattern.compile(
            "^" + PREFIX + "(?:" + ING_WORDS + ")(?![\\p{L}\\p{M}\\p{N}])" + TAIL, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern METHOD_HEAD = Pattern.compile(
            "^" + PREFIX + "(?:" + METHOD_WORDS + ")(?![\\p{L}\\p{M}\\p{N}])" + TAIL, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** Headings that end a recipe section (not recipe headings themselves). */
    private static final Pattern STOP_HEAD = Pattern.compile(
            "^(?:notes?|tips?|tricks?|serving|serves|servings|to\\s+serve|nutrition|timestamps?|chapters?|links?|follow|subscribe"
                    + "|connect|social|music|equipment|disclaimer|about|business|contact|related|watch|more\\s+videos?)(?![\\p{L}\\p{M}\\p{N}]).*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NOISE = Pattern.compile(
            "(?i)https?://|www\\.|@\\w|#\\w|\\b(subscribe|follow me|follow us|instagram|facebook|like,? share|sponsor|click the bell)\\b");
    private static final Pattern LEADING_JUNK = Pattern.compile("^[^\\p{L}\\p{M}\\p{N}\"'(\\u00bc-\\u00be\\u2150-\\u215e]+");
    private static final Pattern NUMBERING = Pattern.compile(
            "^(?:(?:step|stage)\\s*\\d{1,3}\\s*[:.)\\-\\u2013]?\\s*|\\(?\\d{1,3}\\s*[.)\\]]\\s+|\\(?\\d{1,3}\\s*[.)\\]](?=\\p{L}))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKER = Pattern.compile(
            "^\\s*(?:[\\-\\u2013\\u2014\\u2022*\\u25cf\\u25aa\\u25ba\\u25b6\\u2714\\u2705\\u27a1\\u2192]|\\p{So}|\\(?\\d{1,3}\\s*[.)\\]]|(?i:step\\s*\\d))");

    public Result split(String description, List<String> comments) {
        Candidate best = null;
        FoundIn bestIn = FoundIn.NONE;
        Candidate fromDescription = parse(description);
        if (fromDescription.score() > 0) {
            best = fromDescription;
            bestIn = FoundIn.DESCRIPTION;
        }
        if (comments != null) {
            for (String comment : comments) {
                Candidate c = parse(comment);
                if (c.score() > (best == null ? 0 : best.score())) {
                    best = c;
                    bestIn = FoundIn.COMMENT;
                }
            }
        }
        if (best == null) return Result.none();
        boolean strong = best.ingredients.size() >= 2 && best.steps.size() >= 2;
        return new Result(best.ingredients, best.steps, strong ? Confidence.HIGH : Confidence.LOW, bestIn);
    }

    private record Candidate(List<String> ingredients, List<String> steps) {
        int score() {
            return ingredients.size() + steps.size();
        }
    }

    private enum Section { NONE, INGREDIENTS, METHOD }

    private Candidate parse(String text) {
        List<String> ingredients = new ArrayList<>();
        List<String> steps = new ArrayList<>();
        if (text == null || text.isBlank()) return new Candidate(ingredients, steps);

        Section section = Section.NONE;
        boolean blankBefore = false;
        boolean markered = false; // does this section use bullets or numbers?
        int items = 0;
        for (String rawLine : text.split("\\R", -1)) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                blankBefore = true;
                continue;
            }
            String cleaned = clean(line);

            Section head = Section.NONE;
            String inline = "";
            if (cleaned.length() <= 80 || cleaned.contains(":")) {
                Matcher mi = ING_HEAD.matcher(cleaned);
                Matcher mm = METHOD_HEAD.matcher(cleaned);
                if (isHeading(mi)) { head = Section.INGREDIENTS; inline = mi.group(2); }
                else if (isHeading(mm)) { head = Section.METHOD; inline = mm.group(2); }
            }
            if (head != Section.NONE) {
                section = head;
                blankBefore = false;
                markered = false;
                items = 0;
                if (!inline.isBlank()) {
                    items++;
                    add(section, inline, ingredients, steps);
                }
                continue;
            }
            if (section == Section.NONE) {
                blankBefore = false;
                continue;
            }
            if (isStopHeading(cleaned) || NOISE.matcher(line).find()) {
                section = Section.NONE;
                blankBefore = false;
                continue;
            }
            boolean hasMarker = MARKER.matcher(line).find();
            if (blankBefore && items > 0 && !hasMarker && markered) {
                // A gap, then plain text after a bulleted list: unrelated.
                section = Section.NONE;
                blankBefore = false;
                continue;
            }
            blankBefore = false;
            if (cleaned.endsWith(":") && !hasMarker) continue; // sub label like "For the gravy:"
            if (hasMarker) markered = true;
            items++;
            add(section, cleaned, ingredients, steps);
        }
        return new Candidate(dedupe(ingredients), dedupe(steps));
    }

    private static boolean isHeading(Matcher m) {
        if (!m.matches()) return false;
        boolean colon = m.group(1) != null;
        String tail = m.group(2) == null ? "" : m.group(2).strip();
        return tail.isEmpty() || (colon && !tail.isEmpty());
    }

    private static boolean isStopHeading(String cleaned) {
        return cleaned.length() <= 60 && STOP_HEAD.matcher(cleaned).matches();
    }

    /** Strips leading emoji, bullets and numbering, and trailing emoji, bold markers and spaces. */
    static String clean(String line) {
        String s = LEADING_JUNK.matcher(line.strip()).replaceFirst("");
        s = NUMBERING.matcher(s).replaceFirst("");
        s = LEADING_JUNK.matcher(s).replaceFirst("");
        s = s.replaceAll("[*_#]+$", "").replaceAll("[^\\p{L}\\p{M}\\p{N})\\].:;!?\"'%]+$", "");
        return s.strip();
    }

    private static void add(Section section, String value, List<String> ingredients, List<String> steps) {
        String v = clean(value);
        if (v.isEmpty()) return;
        if (section == Section.INGREDIENTS) {
            if (ingredients.size() < MAX_ITEMS) ingredients.add(cut(v, MAX_INGREDIENT));
        } else if (section == Section.METHOD) {
            if (steps.size() < MAX_ITEMS) steps.add(cut(v, MAX_STEP));
        }
    }

    private static String cut(String v, int max) {
        return v.length() <= max ? v : v.substring(0, max).stripTrailing();
    }

    private static List<String> dedupe(List<String> list) {
        List<String> out = new ArrayList<>();
        for (String s : list) {
            // Same text twice in a row is a copy paste slip. The same step later on may be genuine, so only drop neighbours.
            if (out.isEmpty() || !out.get(out.size() - 1).toLowerCase(Locale.ROOT).equals(s.toLowerCase(Locale.ROOT))) out.add(s);
        }
        return out;
    }
}
