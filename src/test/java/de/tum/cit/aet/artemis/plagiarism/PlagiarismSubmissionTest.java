package de.tum.cit.aet.artemis.plagiarism;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.jplag.Submission;
import de.jplag.java.JavaLanguage;
import de.jplag.text.NaturalLanguage;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class PlagiarismSubmissionTest {

    @ParameterizedTest
    @CsvSource({ "4508-student, student", "4508-user1.test.com, user1.test.com", "4508-user-name, user-name", "4508-user-name.test.com, user-name.test.com",
            "4508-user_1.test.com, user_1.test.com", "4508-student.txt, student.txt" })
    void preservesProgrammingStudentLogin(String submissionName, String expectedLogin) {
        var directory = new File("submissions");
        var jplagSubmission = new Submission(submissionName, new File(directory, submissionName), true, List.of(), new JavaLanguage());

        var submission = PlagiarismSubmission.fromJPlagSubmission(jplagSubmission, new ProgrammingExercise(), directory);

        assertThat(submission.getSubmissionId()).isEqualTo(4508L);
        assertThat(submission.getStudentLogin()).isEqualTo(expectedLogin);
    }

    @ParameterizedTest
    @CsvSource({ "4508-student.txt, student", "4508-user1.test.com.txt, user1.test.com", "4508-user-name.txt, user-name", "4508-user-name.test.com.txt, user-name.test.com",
            "4508-user_1.test.com.txt, user_1.test.com", "4508-student.txt.txt, student.txt" })
    void preservesTextStudentLoginWithoutExportFileExtension(String submissionName, String expectedLogin) {
        var directory = new File("submissions");
        var file = new File(directory, submissionName);
        var jplagSubmission = new Submission(submissionName, file, true, List.of(file), new NaturalLanguage());

        var submission = PlagiarismSubmission.fromJPlagSubmission(jplagSubmission, new TextExercise(), directory);

        assertThat(submission.getSubmissionId()).isEqualTo(4508L);
        assertThat(submission.getStudentLogin()).isEqualTo(expectedLogin);
    }
}
