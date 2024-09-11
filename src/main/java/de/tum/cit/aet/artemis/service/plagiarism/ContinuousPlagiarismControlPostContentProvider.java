package de.tum.cit.aet.artemis.service.plagiarism;

import java.util.Locale;

import de.tum.cit.aet.artemis.domain.plagiarism.PlagiarismCase;

public class ContinuousPlagiarismControlPostContentProvider {

    private static final String POST_CONTENT_EN = "Dear %s,<br><br>After an automatic review of your most recent submission for the exercise \"%s\" in the course \"%s\", we have detected a significant similarity of your submission to the submission of at least one other student. This may be a violation of the <a href=\"%s\">Student Code of Conduct</a> of the School of Computation, Information and Technology that you have agreed upon.<br><br>You can fix this warning by submitting a new version of your solution before the exercise due date.";

    private static final String POST_CONTENT_DE = "Sehr geehrter %s,<br><br>nach einer automatisierten Auswertung Ihrer letzten Einsendung für die Übung \"%s\" in der Lehrveranstaltung \"%s\" haben wir eine signifikante Übereinstimmung Ihrer Einsendung mit der Einsendung von mindestens einem anderen Studenten festgestellt. Dies ist möglicherweise ein Verstoß gegen die <a href=\"%s\">Student Code of Conduct</a> der School of Computation, Information and Technology, der Sie zugestimmt haben. Sie können diese Warnmeldung beheben, indem Sie eine neue Version Ihrer Lösung vor dem Abgabetermin der Übung einreichen";

    private static final String COC_LINK = "/content/documents/student-code-of-conduct.pdf";

    private ContinuousPlagiarismControlPostContentProvider() {
    }

    static String getPostContent(PlagiarismCase plagiarismCase) {
        var locale = Locale.forLanguageTag(plagiarismCase.getStudent().getLangKey());
        var localizedTemplate = locale == Locale.ENGLISH ? POST_CONTENT_EN : POST_CONTENT_DE;

        return String.format(localizedTemplate, plagiarismCase.getStudent().getName(), plagiarismCase.getExercise().getTitle(),
                plagiarismCase.getExercise().getCourseViaExerciseGroupOrCourseMember().getTitle(), COC_LINK);
    }
}
