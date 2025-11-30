package de.tum.cit.aet.artemis.quiz.domain;

public enum QuizAction {

    START_NOW("start-now"), END_NOW("end-now"), SET_VISIBLE("set-visible"), START_BATCH("start-batch");

    private final String value;

    QuizAction(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
