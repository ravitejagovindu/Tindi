package com.tindi.api.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks that every ingredient and step returned by the LLM can be traced back to the source text.
 * No LLM, no network.
 *
 * <p>Text is reduced to lower case word and number tokens (punctuation and emoji disappear). An item is
 * compared with every run of up to {@value #WINDOW} neighbouring lines or sentences of each source:
 * <ul>
 *   <li><b>Words</b> (without filler words and units) must be found there. Fewer than half found means
 *       the item is {@link Verdict#NOT_FOUND}. Some missing, or only matched with a spelling difference,
 *       means {@link Verdict#PARTIAL}.</li>
 *   <li><b>Quantities</b> (digits, fractions, number words and units) must be there too, exactly as written,
 *       and a number must stand next to the same unit or word as in the item. Otherwise the item is
 *       {@link Verdict#UNVERIFIED_QUANTITY}.</li>
 * </ul>
 * The check cannot judge meaning: dropping a "not", or words copied from unrelated text in a source, pass.
 */
public class GroundingCheck {
    public enum Kind { TITLE, DESCRIPTION, COMMENT, PASTED }

    public record Source(Kind kind, String text) {}

    public enum Verdict { VERIFIED, PARTIAL, UNVERIFIED_QUANTITY, NOT_FOUND }

    /** The verdict for one item, and the kinds of source its words were found in. */
    public record Check(Verdict verdict, Set<Kind> foundIn) {}

    /**
     * The result for a whole recipe. {@code ingredients} and {@code steps} are the items that survive, and
     * {@code ingredientOrigins} and {@code stepOrigins} give the position each of them had in the input.
     * {@code failed} counts dropped items plus items with an unverified quantity. {@code support} says how
     * many surviving items were found in each kind of source.
     */
    public record Report(List<String> ingredients, List<String> steps, List<Integer> ingredientOrigins,
                         List<Integer> stepOrigins, List<String> warnings, int total, int failed, int dropped,
                         Map<Kind, Integer> support) {
        public boolean majorityFailed() {
            return failed * 2 > total;
        }
    }

    private static final int WINDOW = 3;
    private static final double MIN_WORD_COVERAGE = 0.5;

    private static final Set<String> STOP_WORDS = Set.of(("a an the and or of to in into on onto at for from with "
            + "without by as is are be it its this that these those then now till until while when some your you "
            + "we i them their there here also all if so "
            + "aur ko ka ki ke mein me se par phir fir tak hai hain liye lein le "
            + "और को का की के में से पर फिर तक है हैं लिए").split(" "));

    private static final Set<String> NUMBER_WORDS = words("one two three four five six seven eight nine ten "
            + "eleven twelve half quarter dozen एक दो तीन चार पांच पाँच छह सात आठ नौ दस आधा आधी चौथाई aadha adha");

    private static final Set<String> UNITS = words("g gm gms gram grams kg kgs mg ml l ltr litre litres liter "
            + "liters cup cups tbsp tbsps tbs tablespoon tablespoons tsp tsps teaspoon teaspoons pinch pinches "
            + "handful handfuls pc pcs clove cloves slice slices inch inches cm mm oz ounce ounces lb lbs pound "
            + "pounds min mins minute minutes sec secs second seconds hr hrs hour hours whistle whistles degree "
            + "degrees चम्मच कप ग्राम किलो लीटर चुटकी मिनट घंटा घंटे सीटी chammach chamach chutki seeti");

    /** A number (with decimals, a fraction or a range), or a word. */
    private static final Pattern TOKEN = Pattern.compile(
            "(\\p{Nd}+(?:\\s*[-\\u2013]\\s*\\p{Nd}+|[.,/\\u2044]\\p{Nd}+)*)|(\\p{L}[\\p{L}\\p{M}]*)");
    private static final Pattern BARE_FRACTION = Pattern.compile("\\d+/\\d+");
    private static final Pattern WHOLE_NUMBER = Pattern.compile("\\d+");
    private static final Pattern KEYCAP = Pattern.compile("[0-9#*]\\uFE0F?\\u20E3");
    private static final Pattern FRACTION = Pattern.compile("[\\u00BC-\\u00BE\\u2150-\\u215E\\u2189]");
    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cf}\\uFE00-\\uFE0F]");
    private static final Pattern LEADING_JUNK = Pattern.compile("^[^\\p{L}\\p{N}]+");
    /** "1. ", "2) " or "Step 3:" at the start of a line is numbering, not a quantity. "1.5" is left alone. */
    private static final Pattern LIST_MARKER = Pattern.compile(
            "^(?:(?:step|stage)\\s*\\d{1,3}(?!\\d)\\s*[:.)\\]\\-\\u2013\\u2014]*|\\d{1,3}\\s*[.)\\]](?!\\d))\\s*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LINE_BREAK = Pattern.compile("\\R");
    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?;\\u0964\\u0965])\\s+");
    private static final Pattern CLAUSE_BREAK = Pattern.compile(",(?!\\p{Nd})|[|\\u2022\\u00B7+]");

    private enum Type { WORD, STOP, UNIT, NUMBER }

    /** {@code clause} groups tokens that are not separated by a comma or similar. */
    private record Token(String text, Type type, int clause) {}

    private enum Match { EXACT, FUZZY }

    private record Segment(Kind kind, List<Token> tokens) {}

    /** Segments of each source, kept apart so a window never spans two sources. */
    private final List<List<Segment>> sources = new ArrayList<>();
    private final Set<String> vocabulary = new java.util.HashSet<>();
    /** Item token -> the source words that count as that token. */
    private final Map<Token, Map<String, Match>> cache = new HashMap<>();

    public GroundingCheck(List<Source> input) {
        for (Source source : input) {
            if (source.text() == null) continue;
            List<Segment> segments = new ArrayList<>();
            for (String line : LINE_BREAK.split(KEYCAP.matcher(source.text()).replaceAll(" "))) {
                for (String sentence : SENTENCE_BREAK.split(line)) {
                    List<Token> tokens = tokenize(stripMarker(sentence));
                    if (tokens.isEmpty()) continue;
                    segments.add(new Segment(source.kind(), tokens));
                    tokens.forEach(t -> vocabulary.add(t.text()));
                }
            }
            if (!segments.isEmpty()) sources.add(segments);
        }
    }

    /** Checks a whole recipe. Blank items are ignored. */
    public Report review(List<String> ingredients, List<String> steps) {
        Tally tally = new Tally();
        Kept keptIngredients = reviewList("ingredient", ingredients, tally);
        Kept keptSteps = reviewList("step", steps, tally);
        return new Report(keptIngredients.items(), keptSteps.items(), keptIngredients.origins(), keptSteps.origins(),
                List.copyOf(tally.warnings), tally.total, tally.failed, tally.dropped, tally.support);
    }

    private record Kept(List<String> items, List<Integer> origins) {}

    private static final class Tally {
        final List<String> warnings = new ArrayList<>();
        final Map<Kind, Integer> support = new EnumMap<>(Kind.class);
        int total;
        int failed;
        int dropped;
    }

    private Kept reviewList(String label, List<String> items, Tally tally) {
        List<String> kept = new ArrayList<>();
        List<Integer> origins = new ArrayList<>();
        int dropped = 0;
        for (int position = 0; position < items.size(); position++) {
            String raw = items.get(position);
            String item = raw == null ? "" : raw.strip();
            if (item.isEmpty()) continue;
            tally.total++;
            Check c = check(item);
            if (c.verdict() == Verdict.NOT_FOUND) {
                dropped++;
                tally.failed++;
                tally.dropped++;
                continue;
            }
            kept.add(item);
            origins.add(position);
            c.foundIn().forEach(k -> tally.support.merge(k, 1, Integer::sum));
            // Warnings name the position in the returned list and never repeat the text.
            if (c.verdict() == Verdict.UNVERIFIED_QUANTITY) {
                tally.failed++;
                tally.warnings.add("Check " + label + " " + kept.size()
                        + ": a number or quantity does not match the source text.");
            } else if (c.verdict() == Verdict.PARTIAL) {
                tally.warnings.add("Check " + label + " " + kept.size()
                        + ": it is worded differently from the source text.");
            }
        }
        if (dropped > 0) {
            tally.warnings.add("Removed " + dropped + " " + label + (dropped == 1 ? " that is" : "s that are")
                    + " not in the source text.");
        }
        return new Kept(kept, origins);
    }

    /** Checks one ingredient or step. */
    public Check check(String item) {
        List<Token> tokens = tokenize(stripMarker(KEYCAP.matcher(item).replaceAll(" ")));
        List<Token> words = tokens.stream().filter(t -> t.type() == Type.WORD).toList();
        // "2 cloves" has no plain words: then the units and numbers have to carry the match.
        if (words.isEmpty()) words = tokens.stream().filter(t -> t.type() != Type.STOP).toList();
        if (words.isEmpty()) words = tokens;
        if (words.isEmpty()) return new Check(Verdict.NOT_FOUND, Set.of());

        int best = 0;
        boolean quantitiesOk = false;
        boolean exact = false;
        Set<Kind> foundIn = EnumSet.noneOf(Kind.class);
        for (List<Segment> segments : sources) {
            for (int from = 0; from < segments.size(); from++) {
                for (int to = from + 1; to <= Math.min(from + WINDOW, segments.size()); to++) {
                    List<Segment> window = segments.subList(from, to);
                    int found = 0;
                    boolean allExact = true;
                    for (Token word : words) {
                        Match m = find(word, window);
                        if (m != null) found++;
                        if (m != Match.EXACT) allExact = false;
                    }
                    if (found == 0 || found < best) continue;
                    if (found > best) {
                        best = found;
                        quantitiesOk = false;
                        exact = false;
                        foundIn.clear();
                    }
                    foundIn.add(window.get(0).kind());
                    if (quantitiesMatch(tokens, window)) {
                        quantitiesOk = true;
                        exact |= allExact;
                    }
                }
            }
        }
        if (best < words.size() * MIN_WORD_COVERAGE || best == 0) return new Check(Verdict.NOT_FOUND, Set.of());
        if (!quantitiesOk) return new Check(Verdict.UNVERIFIED_QUANTITY, foundIn);
        return new Check(best == words.size() && exact ? Verdict.VERIFIED : Verdict.PARTIAL, foundIn);
    }

    private Match find(Token wanted, List<Segment> window) {
        Map<String, Match> accepted = matchesFor(wanted);
        Match result = null;
        for (Segment segment : window) {
            for (Token t : segment.tokens()) {
                Match m = accepted.get(t.text());
                if (m == Match.EXACT) return m;
                if (m != null) result = m;
            }
        }
        return result;
    }

    /** Every unit in the item is in the window, and every number sits with the same unit or word there. */
    private boolean quantitiesMatch(List<Token> item, List<Segment> window) {
        for (int i = 0; i < item.size(); i++) {
            Token t = item.get(i);
            if (t.type() == Type.UNIT && find(t, window) == null) return false;
            if (t.type() != Type.NUMBER) continue;
            int number = i;
            List<Integer> anchors = anchorsFor(item, number, window);
            if (anchors.isEmpty()) {
                if (find(t, window) == null) return false;
            } else if (anchors.stream().noneMatch(a -> standTogether(t, item.get(a), a - number, window))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Positions of what a number belongs to: the unit or the run of words right after it in its clause
     * ("2 cups", "10-12 curry leaves"), else the nearest one before it. A unit is binding even if the source
     * lacks it. A word the source does not have is skipped.
     */
    private List<Integer> anchorsFor(List<Token> item, int number, List<Segment> window) {
        int clause = item.get(number).clause();
        List<Integer> after = new ArrayList<>();
        for (int i = number + 1; i < item.size() && item.get(i).clause() == clause; i++) {
            Token t = item.get(i);
            if (t.type() == Type.UNIT) return after.isEmpty() ? List.of(i) : after;
            if (t.type() == Type.WORD && find(t, window) != null) after.add(i);
            else if (!after.isEmpty()) break;
        }
        if (!after.isEmpty()) return after;
        for (int i = number - 1; i >= 0 && item.get(i).clause() == clause; i--) {
            Token t = item.get(i);
            if (t.type() == Type.UNIT || (t.type() == Type.WORD && find(t, window) != null)) return List.of(i);
        }
        return List.of();
    }

    /** True if one clause of the window holds the number and its anchor in about the same relative position. */
    private boolean standTogether(Token number, Token anchor, int gap, List<Segment> window) {
        Map<String, Match> anchors = matchesFor(anchor);
        for (Segment segment : window) {
            List<Token> tokens = segment.tokens();
            for (int n = 0; n < tokens.size(); n++) {
                if (!tokens.get(n).text().equals(number.text())) continue;
                for (int a = 0; a < tokens.size(); a++) {
                    if (tokens.get(a).clause() != tokens.get(n).clause() || !anchors.containsKey(tokens.get(a).text())) {
                        continue;
                    }
                    int d = a - n;
                    // Allow a couple of extra words, or the swap "2 eggs" <-> "eggs: 2".
                    boolean near = gap > 0 ? (d >= 1 && d <= gap + 2) || d == -1 : (d <= -1 && d >= gap - 2) || d == 1;
                    if (near) return true;
                }
            }
        }
        return false;
    }

    private Map<String, Match> matchesFor(Token wanted) {
        return cache.computeIfAbsent(wanted, w -> {
            Map<String, Match> out = new HashMap<>();
            for (String candidate : vocabulary) {
                Match m = compare(w, candidate);
                if (m != null) out.put(candidate, m);
            }
            return out;
        });
    }

    private static Match compare(Token wanted, String candidate) {
        String a = wanted.text();
        if (a.equals(candidate)) return Match.EXACT;
        if (wanted.type() == Type.NUMBER) return null; // numbers must be exactly as written
        String shorter = a.length() <= candidate.length() ? a : candidate;
        String longer = shorter == a ? candidate : a;
        if (shorter.length() >= 3 && (longer.equals(shorter + "s") || longer.equals(shorter + "es"))) return Match.EXACT;
        if (wanted.type() != Type.WORD) return null; // "tsp" is not nearly "tbsp"
        if (shorter.length() >= 4 && longer.startsWith(shorter) && longer.length() - shorter.length() <= 3) {
            return Match.EXACT; // boil / boiled, cream / creamy
        }
        if (shorter.length() >= 5 && a.charAt(0) == candidate.charAt(0)) {
            int allowed = shorter.length() >= 9 ? 2 : 1;
            if (longer.length() - shorter.length() <= allowed && distance(a, candidate) <= allowed) return Match.FUZZY;
        }
        return null;
    }

    /** Edit distance where swapping two neighbouring letters counts as one edit. */
    private static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }

    private static String stripMarker(String line) {
        String s = LEADING_JUNK.matcher(line.strip()).replaceFirst("");
        return LIST_MARKER.matcher(s).replaceFirst("");
    }

    private static List<Token> tokenize(String text) {
        // "1½" must become "1 1/2", so fractions are spaced out before the general normalisation.
        Matcher fraction = FRACTION.matcher(text);
        String spaced = fraction.replaceAll(r -> " " + Normalizer.normalize(r.group(), Normalizer.Form.NFKC) + " ");
        String normal = Normalizer.normalize(spaced, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        normal = INVISIBLE.matcher(normal).replaceAll("");

        List<Token> out = new ArrayList<>();
        int clause = 0;
        for (String part : CLAUSE_BREAK.split(normal)) {
            Matcher m = TOKEN.matcher(part);
            while (m.find()) {
                if (m.group(1) != null) {
                    addNumber(out, canonicalNumber(m.group(1)), clause);
                } else {
                    String w = m.group(2);
                    Type type = NUMBER_WORDS.contains(w) ? Type.NUMBER
                            : UNITS.contains(w) ? Type.UNIT
                            : STOP_WORDS.contains(w) ? Type.STOP : Type.WORD;
                    out.add(new Token(w, type, clause));
                }
            }
            clause++;
        }
        return out;
    }

    /** "1 1/2" is one quantity, so "1" on its own does not count as the same. */
    private static void addNumber(List<Token> out, String number, int clause) {
        if (BARE_FRACTION.matcher(number).matches() && !out.isEmpty()) {
            Token last = out.get(out.size() - 1);
            if (last.clause() == clause && last.type() == Type.NUMBER && WHOLE_NUMBER.matcher(last.text()).matches()) {
                out.set(out.size() - 1, new Token(last.text() + " " + number, Type.NUMBER, clause));
                return;
            }
        }
        out.add(new Token(number, Type.NUMBER, clause));
    }

    /**
     * Devanagari and other digits compare equal to the same ASCII digits. A range such as "10 - 12" becomes
     * "10-12".
     */
    private static String canonicalNumber(String number) {
        StringBuilder sb = new StringBuilder(number.length());
        number.codePoints().forEach(c -> {
            if (Character.isDigit(c)) sb.append(Character.digit(c, 10));
            else if (c == '⁄') sb.append('/');
            else if (c == '–') sb.append('-');
            else if (!Character.isWhitespace(c)) sb.appendCodePoint(c);
        });
        return sb.toString();
    }

    /** Word lists are normalised like all other text (matters for Devanagari letters with a nukta or similar). */
    private static Set<String> words(String list) {
        return Set.of(Normalizer.normalize(list, Normalizer.Form.NFKC).split(" "));
    }
}
