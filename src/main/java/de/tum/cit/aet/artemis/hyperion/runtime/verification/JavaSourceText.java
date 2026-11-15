package de.tum.cit.aet.artemis.hyperion.runtime.verification;

/** Lexical comment removal shared by core admission and worker source checks. */
public final class JavaSourceText {

    private JavaSourceText() {
    }

    /**
     * Removes comments while preserving literals and source positions.
     *
     * @param content source text
     * @return text with comments masked
     */
    public static String stripComments(String content) {
        return stripJavaTrivia(content, false);
    }

    /**
     * Masks comments and literals while preserving source positions.
     *
     * @param content source text
     * @return text with comments and literals masked
     */
    public static String maskCommentsAndLiterals(String content) {
        return stripJavaTrivia(content, true);
    }

    private static String stripJavaTrivia(String content, boolean maskLiterals) {
        StringBuilder stripped = new StringBuilder(content.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean inTextBlock = false;
        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);
            char next = i + 1 < content.length() ? content.charAt(i + 1) : '\0';
            if (inLineComment) {
                if (current == '\n' || current == '\r') {
                    inLineComment = false;
                    stripped.append(current);
                }
                else {
                    stripped.append(' ');
                }
            }
            else if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false;
                    stripped.append("  ");
                    i++;
                }
                else {
                    stripped.append(current == '\n' || current == '\r' ? current : ' ');
                }
            }
            else if (inTextBlock) {
                if (current == '\\' && next != '\0') {
                    stripped.append(maskLiterals ? ' ' : current).append(maskLiterals && next != '\n' && next != '\r' ? ' ' : next);
                    i++;
                }
                else if (content.startsWith("\"\"\"", i)) {
                    stripped.append(maskLiterals ? "   " : "\"\"\"");
                    i += 2;
                    inTextBlock = false;
                }
                else {
                    stripped.append(maskLiterals && current != '\n' && current != '\r' ? ' ' : current);
                }
            }
            else if (inString || inChar) {
                stripped.append(maskLiterals && current != '\n' && current != '\r' ? ' ' : current);
                if (current == '\\' && next != '\0') {
                    stripped.append(maskLiterals && next != '\n' && next != '\r' ? ' ' : next);
                    i++;
                }
                else if ((inString && current == '"') || (inChar && current == '\'')) {
                    inString = false;
                    inChar = false;
                }
            }
            else if (current == '/' && next == '/') {
                inLineComment = true;
                stripped.append("  ");
                i++;
            }
            else if (current == '/' && next == '*') {
                inBlockComment = true;
                stripped.append("  ");
                i++;
            }
            else if (content.startsWith("\"\"\"", i)) {
                stripped.append(maskLiterals ? "   " : "\"\"\"");
                i += 2;
                inTextBlock = true;
            }
            else {
                inString = current == '"';
                inChar = current == '\'';
                stripped.append(maskLiterals && (inString || inChar) ? ' ' : current);
            }
        }
        return stripped.toString();
    }

}
