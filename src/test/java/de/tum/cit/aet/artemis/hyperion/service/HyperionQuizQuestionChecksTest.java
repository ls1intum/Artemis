package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizAnswerOptionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.service.HyperionQuizQuestionChecks.Defect;

class HyperionQuizQuestionChecksTest {

    private static final Set<QuizQuestionGenerationType> ALL_TYPES = Set.of(QuizQuestionGenerationType.values());

    @Test
    void defectOf_acceptsAWellFormedQuestion() {
        assertThat(defectOf(question("Which method is idempotent?", "PUT", "POST", "PATCH", "GET"))).isEmpty();
    }

    @Test
    void defectOf_rejectsATypeTheRequestDidNotAskFor() {
        GeneratedQuizQuestionDTO question = question("Which method is idempotent?", "PUT", "POST", "PATCH", "GET");

        assertThat(HyperionQuizQuestionChecks.defectOf(question, Set.of(QuizQuestionGenerationType.MULTIPLE_CHOICE))).contains(Defect.TYPE_NOT_REQUESTED);
    }

    @Test
    void defectOf_rejectsBlankText() {
        assertThat(defectOf(question("  ", "PUT", "POST", "PATCH", "GET"))).contains(Defect.BLANK_TEXT);
    }

    @Test
    void defectOf_rejectsATitleOverTheLengthLimit() {
        GeneratedQuizQuestionDTO question = new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.SINGLE_CHOICE, "t".repeat(501), "Which method is idempotent?",
                options("PUT", "POST", "PATCH", "GET"), null, null);

        assertThat(defectOf(question)).contains(Defect.TEXT_TOO_LONG);
    }

    @Test
    void defectOf_rejectsAReferenceToMaterialTheQuestionDoesNotContain() {
        assertThat(defectOf(question("Which variable leaves the basis in the following tableau?", "x1", "x2", "x3", "x4"))).contains(Defect.DANGLING_REFERENCE);
    }

    @ParameterizedTest
    @ValueSource(strings = { "the following table", "the following tableau", "the given graph", "the shown network", "the presented matrix", "das folgende Programm",
            "die gegebene Tabelle", "der gezeigte Graph", "der gezeigte Graphen", "die dargestellte Matrix" })
    void defectOf_rejectsEnglishAndGermanReferencesToMissingArtifacts(String reference) {
        assertThat(defectOf(question("Consider " + reference + ". Which answer is correct?", "Alpha", "Beta", "Gamma", "Delta"))).contains(Defect.DANGLING_REFERENCE);
    }

    @Test
    void defectOf_acceptsAReferenceWhenTheQuestionCarriesADataBlock() {
        assertThat(defectOf(question("Which variable leaves the basis in the following tableau?\n| x1 | x2 |\n| 1 | 2 |", "x1", "x2", "x3", "x4"))).isEmpty();
    }

    @Test
    void defectOf_rejectsTheWrongNumberOfOptions() {
        assertThat(defectOf(question("Which method is idempotent?", "PUT", "POST", "PATCH"))).contains(Defect.OPTION_COUNT);
    }

    @Test
    void defectOf_requiresExactlyTwoOptionsForTrueFalse() {
        GeneratedQuizQuestionDTO twoOptions = new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.TRUE_FALSE, "Idempotency", "PUT is idempotent.", options("True", "False"),
                null, null);
        GeneratedQuizQuestionDTO fourOptions = new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.TRUE_FALSE, "Idempotency", "PUT is idempotent.",
                options("True", "False", "Sometimes", "Never"), null, null);

        assertThat(defectOf(twoOptions)).isEmpty();
        assertThat(defectOf(fourOptions)).contains(Defect.OPTION_COUNT);
    }

    @Test
    void defectOf_rejectsABlankOption() {
        assertThat(defectOf(question("Which method is idempotent?", "PUT", " ", "PATCH", "GET"))).contains(Defect.BLANK_OPTION);
    }

    @Test
    void defectOf_rejectsDuplicateOptionsIgnoringCase() {
        assertThat(defectOf(question("Which method is idempotent?", "PUT", "put", "PATCH", "GET"))).contains(Defect.DUPLICATE_OPTIONS);
    }

    @Test
    void defectOf_rejectsOptionsThatDifferOnlyInPunctuationOrSpacing() {
        assertThat(defectOf(question("Which method is idempotent?", "PUT", "PUT.", "PATCH", "GET"))).contains(Defect.DUPLICATE_OPTIONS);
        assertThat(defectOf(question("Which method is idempotent?", "a vertex", "a  vertex ", "an edge", "a face"))).contains(Defect.DUPLICATE_OPTIONS);
    }

    @Test
    void defectOf_rejectsABannedConstruction() {
        assertThat(defectOf(question("Which method is idempotent?", "PUT", "POST", "PATCH", "All of the above"))).contains(Defect.BANNED_CONSTRUCTION);
    }

    @Test
    void defectOf_rejectsAnOptionContainedInAnother() {
        assertThat(defectOf(question("Where is the optimum attained?", "a vertex", "a vertex of the polytope", "an edge", "the interior"))).contains(Defect.CONTAINED_OPTION);
    }

    @Test
    void defectOf_doesNotTreatANumericSubstringAsContainment() {
        assertThat(defectOf(question("How many constraints are binding?", "2", "12", "3", "4"))).isEmpty();
    }

    @Test
    void defectOf_rejectsTheWrongNumberOfCorrectOptions() {
        GeneratedQuizQuestionDTO twoCorrectSingleChoice = new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.SINGLE_CHOICE, "HTTP", "Which methods are idempotent?",
                List.of(option("PUT", true), option("GET", true), option("POST", false), option("PATCH", false)), null, null);
        GeneratedQuizQuestionDTO allCorrectMultipleChoice = new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.MULTIPLE_CHOICE, "HTTP", "Which methods exist?",
                List.of(option("PUT", true), option("GET", true), option("POST", true), option("PATCH", true)), null, null);
        GeneratedQuizQuestionDTO noneCorrectMultipleChoice = new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.MULTIPLE_CHOICE, "HTTP", "Which methods are idempotent?",
                List.of(option("PUT", false), option("GET", false), option("POST", false), option("PATCH", false)), null, null);

        assertThat(defectOf(twoCorrectSingleChoice)).contains(Defect.CORRECT_COUNT);
        assertThat(defectOf(allCorrectMultipleChoice)).contains(Defect.CORRECT_COUNT);
        assertThat(defectOf(noneCorrectMultipleChoice)).contains(Defect.CORRECT_COUNT);
    }

    @Test
    void defectOf_acceptsUpToThreeCorrectOptionsForMultipleChoice() {
        GeneratedQuizQuestionDTO question = new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.MULTIPLE_CHOICE, "HTTP", "Which methods are idempotent?",
                List.of(option("PUT", true), option("GET", true), option("DELETE", true), option("POST", false)), null, null);

        assertThat(defectOf(question)).isEmpty();
    }

    @Test
    void defectOf_rejectsAConspicuouslyLongCorrectOption() {
        GeneratedQuizQuestionDTO question = question("Which method is idempotent?", "PUT, because repeating the same request leaves the resource in the same state", "POST",
                "PATCH", "CONNECT");

        assertThat(defectOf(question)).contains(Defect.CONSPICUOUS_LENGTH);
    }

    @Test
    void optionCountFor_asksTwoOptionsForTrueFalseAndFourOtherwise() {
        assertThat(HyperionQuizQuestionChecks.optionCountFor(QuizQuestionGenerationType.TRUE_FALSE)).isEqualTo(2);
        assertThat(HyperionQuizQuestionChecks.optionCountFor(QuizQuestionGenerationType.SINGLE_CHOICE)).isEqualTo(4);
        assertThat(HyperionQuizQuestionChecks.optionCountFor(QuizQuestionGenerationType.MULTIPLE_CHOICE)).isEqualTo(4);
    }

    @Test
    void defectOf_rejectsAQuestionTextThatEnumeratesTheOptions() {
        assertThat(defectOf(question("Which method is idempotent?\n\nA) PUT\nB) POST\nC) PATCH\nD) GET", "PUT", "POST", "PATCH", "GET"))).contains(Defect.OPTIONS_IN_QUESTION_TEXT);
        assertThat(defectOf(question("Which method is idempotent?\n- **a:** the first\n- **b:** the second", "PUT", "POST", "PATCH", "GET")))
                .contains(Defect.OPTIONS_IN_QUESTION_TEXT);
    }

    @Test
    void defectOf_rejectsAQuestionTextThatRepeatsTheOptions() {
        assertThat(defectOf(question("Which method is idempotent: PUT, POST, PATCH or DELETE?", "PUT", "POST", "PATCH", "DELETE"))).contains(Defect.OPTIONS_IN_QUESTION_TEXT);
    }

    @Test
    void defectOf_rejectsOptionsThatReferToStatementsOfTheText() {
        GeneratedQuizQuestionDTO question = new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.MULTIPLE_CHOICE, "Logic",
                "Which statements are correct?\n1. OR is $x_1 + x_2 \\geq 1$.\n2. AND is $x_1 + x_2 = 2$.\n3. XOR is $x_1 = x_2$.\n4. NOT is $1 - x_1$.",
                List.of(option("Aussage 1 ist korrekt.", true), option("Aussage 2 ist korrekt.", true), option("Statement 3 is correct.", false),
                        option("Statement IV is correct.", false)),
                null, null);

        assertThat(defectOf(question)).contains(Defect.OPTIONS_IN_QUESTION_TEXT);
    }

    @Test
    void defectOf_acceptsNumberedRulesAndSingleMentionsOfAnOption() {
        GeneratedQuizQuestionDTO question = new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.MULTIPLE_CHOICE, "Rules",
                "Two rules hold:\n1. Project A and project B exclude each other.\n2. Project A requires project B.\nIn case a project is chosen, which constraints are correct?",
                List.of(option("$x_A + x_B \\leq 1$ models rule 1", true), option("$x_A \\leq x_B$ models rule 2", true), option("$x_A + x_B = 1$ models rule 1", false),
                        option("$x_B \\leq x_A$ models rule 2", false)),
                null, null);

        assertThat(defectOf(question)).isEmpty();
        assertThat(defectOf(question("Is PUT idempotent, and which method is?", "PUT", "POST", "PATCH", "GET"))).isEmpty();
    }

    @Test
    void defectOf_rejectsLatexOutsideMathDelimiters() {
        assertThat(defectOf(question("Which constraint models the implication?", "x \\leq y", "x \\geq y", "x = y", "x + y = 1"))).contains(Defect.UNDELIMITED_MATH);
        assertThat(defectOf(question("What is the decimal value of \\frac{1}{2}?", "0.5", "0.2", "1.2", "2.0"))).contains(Defect.UNDELIMITED_MATH);
    }

    @Test
    void defectOf_acceptsLatexInsideMathDelimitersAndCode() {
        assertThat(defectOf(question("Which constraint models $x \\Rightarrow y$?\n$$\n\\max \\; x\n$$", "$x \\leq y$", "$x \\geq y$", "$x = y$", "$x + y = 1$"))).isEmpty();
        assertThat(defectOf(question("What does `System.out.print(\"a\\nb\")` print?", "Two lines", "One line", "Nothing", "An error"))).isEmpty();
    }

    @Test
    void normalise_collapsesCaseAndWhitespace() {
        assertThat(HyperionQuizQuestionChecks.normalise("  Which  Method\nis Idempotent? ")).isEqualTo("which method is idempotent?");
    }

    private static Optional<Defect> defectOf(GeneratedQuizQuestionDTO question) {
        return HyperionQuizQuestionChecks.defectOf(question, ALL_TYPES);
    }

    private static GeneratedQuizQuestionDTO question(String text, String correct, String... distractors) {
        return new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.SINGLE_CHOICE, "HTTP Methods", text, options(correct, distractors), null, null);
    }

    private static List<GeneratedQuizAnswerOptionDTO> options(String correct, String... distractors) {
        List<GeneratedQuizAnswerOptionDTO> options = new ArrayList<>();
        options.add(option(correct, true));
        for (String distractor : distractors) {
            options.add(option(distractor, false));
        }
        return options;
    }

    private static GeneratedQuizAnswerOptionDTO option(String text, boolean correct) {
        return new GeneratedQuizAnswerOptionDTO(text, correct, null, null);
    }
}
