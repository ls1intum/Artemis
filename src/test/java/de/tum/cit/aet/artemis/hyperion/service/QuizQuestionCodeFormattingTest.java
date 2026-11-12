package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuizQuestionCodeFormattingTest {

    @Test
    void formatCode_setsAnOptionThatIsAStatementInBackticks() {
        assertThat(QuizQuestionCodeFormatting.formatCode("if (len == a.length) { return 1; }")).isEqualTo("`if (len == a.length) { return 1; }`");
        assertThat(QuizQuestionCodeFormatting.formatCode("public static int sum(int n) { if (n == 0) return 0; return n + sum(n - 1); }"))
                .isEqualTo("`public static int sum(int n) { if (n == 0) return 0; return n + sum(n - 1); }`");
        assertThat(QuizQuestionCodeFormatting.formatCode("int[] a = new int[3];")).isEqualTo("`int[] a = new int[3];`");
        assertThat(QuizQuestionCodeFormatting.formatCode("  x = 5;  ")).isEqualTo("`x = 5;`");
        assertThat(QuizQuestionCodeFormatting.formatCode("int count = items.size();")).isEqualTo("`int count = items.size();`");
        assertThat(QuizQuestionCodeFormatting.formatCode("throw new IllegalArgumentException();")).isEqualTo("`throw new IllegalArgumentException();`");
    }

    @Test
    void formatCode_leavesProseAlone() {
        assertThat(QuizQuestionCodeFormatting.formatCode("It should rethrow a new IllegalArgumentException and need not declare any throws clause."))
                .isEqualTo("It should rethrow a new IllegalArgumentException and need not declare any throws clause.");
        assertThat(QuizQuestionCodeFormatting.formatCode("What is the output of System.out.println(a[1]);")).isEqualTo("What is the output of System.out.println(a[1]);");
        assertThat(QuizQuestionCodeFormatting.formatCode("String literals are immutable in Java;")).isEqualTo("String literals are immutable in Java;");
        assertThat(QuizQuestionCodeFormatting.formatCode("Die Methode gibt 0 zurück;")).isEqualTo("Die Methode gibt 0 zurück;");
        assertThat(QuizQuestionCodeFormatting.formatCode("")).isEmpty();
        assertThat(QuizQuestionCodeFormatting.formatCode(null)).isNull();
    }

    @Test
    void formatCode_leavesTextWithMarkdownCodeMathOrListsAlone() {
        assertThat(QuizQuestionCodeFormatting.formatCode("The call `sum(3)` returns 6;")).isEqualTo("The call `sum(3)` returns 6;");
        assertThat(QuizQuestionCodeFormatting.formatCode("\\(\\{x \\mid x > 0\\}\\)")).isEqualTo("\\(\\{x \\mid x > 0\\}\\)");
        assertThat(QuizQuestionCodeFormatting.formatCode("- first item;\n- second item;")).isEqualTo("- first item;\n- second item;");
        assertThat(QuizQuestionCodeFormatting.formatCode("| a | b |\n| 1 | 2 |")).isEqualTo("| a | b |\n| 1 | 2 |");
    }

    @Test
    void formatCode_fencesEveryRunOfCodeLinesInsideProse() {
        String text = "Consider the method:\npublic static int sum(int n) {\n    if (n == 0) {\n        return 0;\n    }\n    return n + sum(n - 1);\n}\n\nWhat does sum(3) return?";

        assertThat(QuizQuestionCodeFormatting.formatCode(text)).isEqualTo("Consider the method:\n```\npublic static int sum(int n) {\n    if (n == 0) {\n        return 0;\n    }\n"
                + "    return n + sum(n - 1);\n}\n```\n\nWhat does sum(3) return?");
    }

    @Test
    void formatCode_fencesATextThatIsCodeOnlyOnce() {
        assertThat(QuizQuestionCodeFormatting.formatCode("int a = 1;\n\nint b = a + 1;\n")).isEqualTo("```\nint a = 1;\n\nint b = a + 1;\n```\n");
    }
}
