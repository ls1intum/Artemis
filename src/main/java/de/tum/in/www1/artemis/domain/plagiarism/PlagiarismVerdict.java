package de.tum.in.www1.artemis.domain.plagiarism;

public enum PlagiarismVerdict {

    PLAGIARISM, POINT_DEDUCTION, WARNING, NO_PLAGIARISM;

    @SuppressWarnings("unused") // Used in the email template plagiarismVerdictEmail.html
    public String getEmailMessage() {
        return switch (this) {
            case PLAGIARISM -> "Your case is considered as plagiarism!";
            case POINT_DEDUCTION -> "Due to your plagiarism, we are deducting points from your score in this exercise!";
            case WARNING -> "Due to the plagiarism attempt, you are warned. Any further plagiarism will lead to severe consequences!";
            case NO_PLAGIARISM -> "The case is no longer considered as plagiarism.";
        };
    }
}
