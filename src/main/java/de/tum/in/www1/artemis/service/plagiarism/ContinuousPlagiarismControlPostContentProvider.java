package de.tum.in.www1.artemis.service.plagiarism;

import java.util.Locale;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;

public class ContinuousPlagiarismControlPostContentProvider {

    private static final String POST_CONTENT_EN = "Dear %s,<br><br>After an automatic review of your most recent submission for the exercise \"%s\" in the course \"%s\", we have detected a significant similarity of your submission to submission of at least one other student. This may be a violation of the <a href=\"%s\">Student Code of Conduct</a> of the School of Computation, Information and Technology that you have agreed upon.<br><br>You can fix this warning by submitting a new version of your solution before the exercise due date.";

    private static final String POST_CONTENT_DE = "Dear %s,<br><br>After an automatic review of your most recent submission for the exercise \"%s\" in the course \"%s\", we have detected a significant similarity of your submission to submission of at least one other student. This may be a violation of the <a href=\"%s\">Student Code of Conduct</a> of the School of Computation, Information and Technology that you have agreed upon.<br><br>You can fix this warning by submitting a new version of your solution before the exercise due date.";

    private static final String COC_LINK = "https://www.in.tum.de/fileadmin/w00bws/in/2.Fur_Studierende/Pruefungen_und_Formalitaeten/1.Gute_studentische_Praxis/englisch/leitfaden-en_2016Jun22.pdf";

    private ContinuousPlagiarismControlPostContentProvider() {
    }

    static String getPostContent(PlagiarismCase plagiarismCase) {
        var locale = Locale.forLanguageTag(plagiarismCase.getStudent().getLangKey());
        var localizedTemplate = locale == Locale.ENGLISH ? POST_CONTENT_EN : POST_CONTENT_DE;

        return String.format(localizedTemplate, plagiarismCase.getStudent().getName(), plagiarismCase.getExercise().getTitle(),
                plagiarismCase.getExercise().getCourseViaExerciseGroupOrCourseMember().getTitle(), COC_LINK);
    }
}
