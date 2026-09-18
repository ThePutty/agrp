package cz.demo.caselaw.ai;

/**
 * Free models answer JSON in whatever wrapper they like: ```json fences, a leading sentence,
 * a trailing explanation. This strips all of that down to the outermost JSON object so a plain
 * Jackson parse succeeds. Used as the last-resort fallback when the AiService parser gives up.
 */
public final class JsonText {

    private JsonText() {
    }

    /** @return the outermost {...} block, or null when the text contains no balanced object */
    public static String extractObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = stripFences(raw.trim());
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{' -> depth++;
                case '}' -> {
                    depth--;
                    if (depth == 0) {
                        return text.substring(start, i + 1);
                    }
                }
                default -> {
                }
            }
        }
        return null;
    }

    static String stripFences(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        int firstNewline = text.indexOf('\n');
        String body = firstNewline < 0 ? text.substring(3) : text.substring(firstNewline + 1);
        int closing = body.lastIndexOf("```");
        return (closing < 0 ? body : body.substring(0, closing)).trim();
    }
}
