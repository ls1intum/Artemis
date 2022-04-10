package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;

public class PlagiarismVerdictDTO {

    private PlagiarismVerdict verdict;

    private String verdictMessage;

    private int verdictPointDeduction;

    public PlagiarismVerdict getVerdict() {
        return verdict;
    }

    public void setVerdict(PlagiarismVerdict verdict) {
        this.verdict = verdict;
    }

    public String getVerdictMessage() {
        return verdictMessage;
    }

    public void setVerdictMessage(String verdictMessage) {
        this.verdictMessage = verdictMessage;
    }

    public int getVerdictPointDeduction() {
        return verdictPointDeduction;
    }

    public void setVerdictPointDeduction(int verdictPointDeduction) {
        this.verdictPointDeduction = verdictPointDeduction;
    }
}
