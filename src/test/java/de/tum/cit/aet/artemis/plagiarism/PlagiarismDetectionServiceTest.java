package de.tum.cit.aet.artemis.plagiarism;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.jplag.Language;
import de.jplag.c.CLanguage;
import de.jplag.cpp.CPPLanguage;
import de.jplag.csharp.CSharpLanguage;
import de.jplag.java.JavaLanguage;
import de.jplag.javascript.JavaScriptLanguage;
import de.jplag.kotlin.KotlinLanguage;
import de.jplag.rust.RustLanguage;
import de.jplag.swift.SwiftLanguage;
import de.jplag.typescript.TypeScriptLanguage;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.exception.ProgrammingLanguageNotSupportedForPlagiarismDetectionException;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismResultRepository;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismDetectionService;
import de.tum.cit.aet.artemis.plagiarism.service.ProgrammingPlagiarismDetectionService;
import de.tum.cit.aet.artemis.plagiarism.service.TextPlagiarismDetectionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingLanguageFeature;
import de.tum.cit.aet.artemis.programming.service.ProgrammingLanguageFeatureService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class PlagiarismDetectionServiceTest {

    private static Path tempPath;

    private final PlagiarismDetectionConfig config = PlagiarismDetectionConfig.createDefault();

    private final TextPlagiarismDetectionService textPlagiarismDetectionService = mock();

    private final ProgrammingLanguageFeatureService programmingLanguageFeatureService = mock();

    private final ProgrammingPlagiarismDetectionService programmingPlagiarismDetectionService = mock();

    private final PlagiarismResultRepository plagiarismResultRepository = mock();

    private final PlagiarismDetectionService service = new PlagiarismDetectionService(textPlagiarismDetectionService, Optional.of(programmingLanguageFeatureService),
            programmingPlagiarismDetectionService, plagiarismResultRepository);

    // Constants for test content
    private static final String SIMPLE_JAVA_CONTENT = "public class Test { public static void main(String[] args) { System.out.println(\"Hello World\"); } }";

    private static final String MINIMAL_JAVA_CONTENT = "class Test { }";

    private static final String COMPLEX_JAVA_CONTENT_TEMPLATE = "public class Test%d { public static void main(String[] args) { System.out.println(\"Hello World\"); } }";

    private static final String NON_JAVA_CONTENT = "This is a readme file with lots of content that should not be counted for Java token analysis";

    @BeforeAll
    static void setTempPath() throws IOException {
        // we cannot rely on spring boot value injection here as it's not an integration test.
        tempPath = Path.of("local", "server-integration-test");
        Files.createDirectories(tempPath);
    }

    @Test
    void shouldExecuteChecksForTextExercise() {
        // given
        var textExercise = new TextExercise();
        textExercise.setPlagiarismDetectionConfig(config);
        var textPlagiarismResult = new PlagiarismResult();
        when(textPlagiarismDetectionService.checkPlagiarism(textExercise, config.getSimilarityThreshold(), config.getMinimumScore(), config.getMinimumSize()))
                .thenReturn(textPlagiarismResult);

        // when
        var result = service.checkTextExercise(textExercise);

        // then
        assertThat(result).isEqualTo(textPlagiarismResult);
    }

    @Test
    void shouldExecuteChecksForProgrammingExercise() throws IOException, ProgrammingLanguageNotSupportedForPlagiarismDetectionException {
        // given
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setId(1L);
        programmingExercise.setPlagiarismDetectionConfig(config);
        var programmingPlagiarismResult = new PlagiarismResult();
        when(programmingPlagiarismDetectionService.checkPlagiarism(1L, config.getSimilarityThreshold(), config.getMinimumScore(), config.getMinimumSize()))
                .thenReturn(programmingPlagiarismResult);

        // and
        var programmingLanguageFeature = new ProgrammingLanguageFeature(null, false, false, true, false, false, emptyList(), false);
        when(programmingLanguageFeatureService.getProgrammingLanguageFeatures(any())).thenReturn(programmingLanguageFeature);

        // when
        var result = service.checkProgrammingExercise(programmingExercise);

        // then
        assertThat(result).isEqualTo(programmingPlagiarismResult);
    }

    @Test
    void shouldThrowExceptionOnUnsupportedProgrammingLanguage() {
        // given
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setPlagiarismDetectionConfig(config);
        var programmingLanguageFeature = new ProgrammingLanguageFeature(null, false, false, false, false, false, emptyList(), false);
        when(programmingLanguageFeatureService.getProgrammingLanguageFeatures(any())).thenReturn(programmingLanguageFeature);

        // expect
        assertThatThrownBy(() -> service.checkProgrammingExercise(programmingExercise)).isInstanceOf(ProgrammingLanguageNotSupportedForPlagiarismDetectionException.class);
    }

    @Test
    void shouldExecuteChecksWithJplagReportForProgrammingExercise() throws ProgrammingLanguageNotSupportedForPlagiarismDetectionException {
        // given
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setId(1L);
        programmingExercise.setPlagiarismDetectionConfig(config);
        var zipFile = Path.of("test.zip").toFile();
        when(programmingPlagiarismDetectionService.checkPlagiarismWithJPlagReport(1L, config.getSimilarityThreshold(), config.getMinimumScore(), config.getMinimumSize()))
                .thenReturn(zipFile);

        // and
        var programmingLanguageFeature = new ProgrammingLanguageFeature(null, false, false, true, false, false, emptyList(), false);
        when(programmingLanguageFeatureService.getProgrammingLanguageFeatures(any())).thenReturn(programmingLanguageFeature);

        // when
        var result = service.checkProgrammingExerciseWithJplagReport(programmingExercise);

        // then
        assertThat(result).isEqualTo(zipFile);
    }

    @Test
    void shouldThrowExceptionOnUnsupportedProgrammingLanguageForChecksWithJplagReport() {
        // given
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setPlagiarismDetectionConfig(config);
        var programmingLanguageFeature = new ProgrammingLanguageFeature(null, false, false, false, false, false, emptyList(), false);
        when(programmingLanguageFeatureService.getProgrammingLanguageFeatures(any())).thenReturn(programmingLanguageFeature);

        // expect
        assertThatThrownBy(() -> service.checkProgrammingExerciseWithJplagReport(programmingExercise))
                .isInstanceOf(ProgrammingLanguageNotSupportedForPlagiarismDetectionException.class);
    }

    // ProgrammingPlagiarismDetectionService specific tests
    @ParameterizedTest
    @MethodSource("provideProgrammingLanguagesForJPlag")
    void testGetJPlagProgrammingLanguage(ProgrammingLanguage programmingLanguage, Class<? extends Language> expectedLanguageClass, String expectedErrorMessage) {
        ProgrammingExercise exercise = createProgrammingExercise(programmingLanguage);

        if (expectedLanguageClass != null) {
            Language result = ProgrammingPlagiarismDetectionService.getJPlagProgrammingLanguage(exercise);
            assertThat(result).isInstanceOf(expectedLanguageClass);
        }
        else {
            assertThatThrownBy(() -> ProgrammingPlagiarismDetectionService.getJPlagProgrammingLanguage(exercise)).isInstanceOf(BadRequestAlertException.class)
                    .hasMessageContaining(expectedErrorMessage);
        }
    }

    private static Stream<Arguments> provideProgrammingLanguagesForJPlag() {
        return Stream.of(
                // Supported languages
                Arguments.of(ProgrammingLanguage.JAVA, JavaLanguage.class, null), Arguments.of(ProgrammingLanguage.C, CLanguage.class, null),
                Arguments.of(ProgrammingLanguage.C_PLUS_PLUS, CPPLanguage.class, null), Arguments.of(ProgrammingLanguage.C_SHARP, CSharpLanguage.class, null),
                Arguments.of(ProgrammingLanguage.JAVASCRIPT, JavaScriptLanguage.class, null), Arguments.of(ProgrammingLanguage.KOTLIN, KotlinLanguage.class, null),
                Arguments.of(ProgrammingLanguage.RUST, RustLanguage.class, null), Arguments.of(ProgrammingLanguage.SWIFT, SwiftLanguage.class, null),
                Arguments.of(ProgrammingLanguage.TYPESCRIPT, TypeScriptLanguage.class, null),

                // Unsupported languages
                Arguments.of(ProgrammingLanguage.PHP, null, "Programming language PHP not supported for plagiarism check."),
                Arguments.of(ProgrammingLanguage.EMPTY, null, "Programming language EMPTY not supported for plagiarism check."));
    }

    @Test
    void testCountTokensInFile_ValidContent() throws IOException {
        Path testFile = createTestFile("test.java", SIMPLE_JAVA_CONTENT);

        int result = FileUtil.countTokensInFile(testFile, 100, 0);

        assertThat(result).isGreaterThan(0);
        // The content should have tokens like: public, class, Test, public, static, void, main, String, args, System, out, println, Hello, World
        assertThat(result).isGreaterThanOrEqualTo(10);
    }

    @Test
    void testCountTokensInFile_EmptyFile() throws IOException {
        Path testFile = createTestFile("empty.java", "");

        int result = FileUtil.countTokensInFile(testFile, 100, 0);

        assertThat(result).isEqualTo(0);
    }

    @Test
    void testCountTokensInFile_ReachesMinimumSize() throws IOException {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            content.append(String.format(COMPLEX_JAVA_CONTENT_TEMPLATE, i)).append(" ");
        }
        Path testFile = createTestFile("large.java", content.toString());

        int result = FileUtil.countTokensInFile(testFile, 100, 0);

        assertThat(result).isGreaterThanOrEqualTo(100);
    }

    @Test
    void testMeetsMinimumSize_ValidRepository() throws IOException {
        Repository repository = createMockRepository();
        ProgrammingExercise exercise = createProgrammingExercise(ProgrammingLanguage.JAVA);
        setupRepositoryWithFile("Test.java", SIMPLE_JAVA_CONTENT, repository);

        boolean result = ProgrammingPlagiarismDetectionService.meetsMinimumSize(repository, exercise, 5);

        assertThat(result).isTrue();
    }

    @Test
    void testMeetsMinimumSize_RepositoryDoesNotMeetMinimum() throws IOException {
        Repository repository = createMockRepository();
        ProgrammingExercise exercise = createProgrammingExercise(ProgrammingLanguage.JAVA);
        setupRepositoryWithFile("Test.java", MINIMAL_JAVA_CONTENT, repository);

        boolean result = ProgrammingPlagiarismDetectionService.meetsMinimumSize(repository, exercise, 100);

        assertThat(result).isFalse();
    }

    @Test
    void testMeetsMinimumSize_MultipleFiles() throws IOException {
        Repository repository = createMockRepository();
        ProgrammingExercise exercise = createProgrammingExercise(ProgrammingLanguage.JAVA);
        setupRepositoryWithMultipleFiles(repository);

        boolean result = ProgrammingPlagiarismDetectionService.meetsMinimumSize(repository, exercise, 20);

        assertThat(result).isTrue();
    }

    @Test
    void testMeetsMinimumSize_IgnoresNonRelevantFiles() throws IOException {
        Repository repository = createMockRepository();
        ProgrammingExercise exercise = createProgrammingExercise(ProgrammingLanguage.JAVA);
        setupRepositoryWithMixedFiles(repository);

        boolean result = ProgrammingPlagiarismDetectionService.meetsMinimumSize(repository, exercise, 10);

        assertThat(result).isFalse(); // Only Java file should be counted
    }

    // Helper methods
    private ProgrammingExercise createProgrammingExercise(ProgrammingLanguage programmingLanguage) {
        Course course = CourseFactory.generateCourse(null, null, null, new HashSet<>());
        return ProgrammingExerciseFactory.generateProgrammingExercise(null, null, course, programmingLanguage);
    }

    private Repository createMockRepository() {
        return mock(Repository.class);
    }

    private Path createTestFile(String filename, String content) throws IOException {
        Path tempDir = createTemporaryDirectory();
        Path testFile = tempDir.resolve(filename);
        FileUtils.writeStringToFile(testFile.toFile(), content, StandardCharsets.UTF_8);
        return testFile;
    }

    private Path createTemporaryDirectory() throws IOException {
        return Files.createTempDirectory(tempPath, "plagiarismRepo");
    }

    private void setupRepositoryWithFile(String filename, String content, Repository repository) throws IOException {
        Path tempDir = createTemporaryDirectory();
        Path repoPath = tempDir.resolve("repo");
        Files.createDirectories(repoPath);
        lenient().when(repository.getLocalPath()).thenReturn(repoPath);

        Path file = repoPath.resolve(filename);
        FileUtils.writeStringToFile(file.toFile(), content, StandardCharsets.UTF_8);
    }

    private void setupRepositoryWithMultipleFiles(Repository repository) throws IOException {
        Path tempDir = createTemporaryDirectory();
        Path repoPath = tempDir.resolve("repo");
        Files.createDirectories(repoPath);
        lenient().when(repository.getLocalPath()).thenReturn(repoPath);

        // Create multiple Java files
        for (int i = 0; i < 3; i++) {
            Path javaFile = repoPath.resolve("Test" + i + ".java");
            String content = String.format(COMPLEX_JAVA_CONTENT_TEMPLATE, i);
            FileUtils.writeStringToFile(javaFile.toFile(), content, StandardCharsets.UTF_8);
        }
    }

    private void setupRepositoryWithMixedFiles(Repository repository) throws IOException {
        Path tempDir = createTemporaryDirectory();
        Path repoPath = tempDir.resolve("repo");
        Files.createDirectories(repoPath);
        lenient().when(repository.getLocalPath()).thenReturn(repoPath);

        // Create a Java file (relevant)
        Path javaFile = repoPath.resolve("Test.java");
        FileUtils.writeStringToFile(javaFile.toFile(), "public class Test { }", StandardCharsets.UTF_8);

        // Create a text file (not relevant)
        Path textFile = repoPath.resolve("readme.txt");
        FileUtils.writeStringToFile(textFile.toFile(), NON_JAVA_CONTENT, StandardCharsets.UTF_8);

    }

}
