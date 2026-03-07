package me.golemcore.plugins.golemcore.mail.support;

import java.util.regex.Pattern;

public final class HtmlSanitizer {

    private static final Pattern BLOCK_TAGS = Pattern.compile("<(br|p|div|tr|li|h[1-6])[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ALL_TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern MULTI_NEWLINES = Pattern.compile("\n{3,}");
    private static final Pattern MULTI_SPACES = Pattern.compile(" {2,}");

    private HtmlSanitizer() {
    }

    public static String stripHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String result = BLOCK_TAGS.matcher(html).replaceAll("\n");
        result = ALL_TAGS.matcher(result).replaceAll("");
        result = decodeEntities(result);
        result = MULTI_NEWLINES.matcher(result).replaceAll("\n\n");
        result = MULTI_SPACES.matcher(result).replaceAll(" ");
        return result.strip();
    }

    private static String decodeEntities(String text) {
        return text
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
    }
}
