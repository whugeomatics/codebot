package io.github.bridgewares.codebot.qa;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TokenUtils {

    private static final Pattern WORD = Pattern.compile("[a-zA-Z0-9_.$/\\\\-]+");

    private TokenUtils() {
    }

    static Set<String> terms(String text) {
        Set<String> terms = new HashSet<>();
        if (text == null || text.isBlank()) {
            return terms;
        }
        Matcher matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = matcher.group();
            if (word.length() >= 2) {
                terms.add(word);
                splitCamelAndPath(word, terms);
            }
        }
        addCjkBigrams(text, terms);
        return terms;
    }

    private static void splitCamelAndPath(String word, Set<String> terms) {
        String normalized = word.replace('\\', '/');
        for (String part : normalized.split("[.$/_\\-]+")) {
            if (part.length() >= 2) {
                terms.add(part);
            }
        }
    }

    private static void addCjkBigrams(String text, Set<String> terms) {
        StringBuilder cjk = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (isCjk(ch)) {
                cjk.append(ch);
            } else {
                flushCjk(cjk, terms);
            }
        }
        flushCjk(cjk, terms);
    }

    private static void flushCjk(StringBuilder cjk, Set<String> terms) {
        if (cjk.length() == 1) {
            terms.add(cjk.toString());
        }
        for (int i = 0; i < cjk.length() - 1; i++) {
            terms.add(cjk.substring(i, i + 2));
        }
        cjk.setLength(0);
    }

    private static boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }
}
