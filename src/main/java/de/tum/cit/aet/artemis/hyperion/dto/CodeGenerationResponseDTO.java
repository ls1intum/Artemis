package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

public class CodeGenerationResponseDTO {

    private List<GeneratedFile> files;

    private String solutionPlan;

    public CodeGenerationResponseDTO(List<GeneratedFile> files, String solutionPlan) {
        this.files = files;
        this.solutionPlan = solutionPlan;
    }

    public List<GeneratedFile> getFiles() {
        return files;
    }

    public void setFiles(List<GeneratedFile> files) {
        this.files = files;
    }

    public String getSolutionPlan() {
        return solutionPlan;
    }

    public void setSolutionPlan(String solutionPlan) {
        this.solutionPlan = solutionPlan;
    }
}
