package de.tum.cit.aet.artemis.lecture.domain;

public class LectureTranscriptionSegment {

    private Double startTime;

    private Double endTime;

    private String text;

    private int slideNumber;

    public LectureTranscriptionSegment() {
    }

    public LectureTranscriptionSegment(Double startTime, Double endTime, String text, int slideNumber) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.text = text;
        this.slideNumber = slideNumber;
    }

    public Double getStartTime() {
        return startTime;
    }

    public void setStartTime(Double startTime) {
        this.startTime = startTime;
    }

    public Double getEndTime() {
        return endTime;
    }

    public void setEndTime(Double endTime) {
        this.endTime = endTime;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getSlideNumber() {
        return slideNumber;
    }

    public void setSlideNumber(int slideNumber) {
        this.slideNumber = slideNumber;
    }

    @Override
    public String toString() {
        return "LectureTranscriptionSegment [startTime = " + startTime + ", endTime = " + endTime + ", text = " + text + "]";
    }
}
