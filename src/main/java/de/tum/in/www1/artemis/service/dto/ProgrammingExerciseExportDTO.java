package de.tum.in.www1.artemis.service.dto;

import java.util.Collections;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.ExampleSubmission;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.StaticCodeAnalysisCategory;
import de.tum.in.www1.artemis.domain.enumeration.*;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseExportDTO {

    private String title;

    private String shortName;

    private Double maxPoints;

    private Double bonusPoints;

    private AssessmentType assessmentType;

    private IncludedInOverallScore includedInOverallScore;

    private String gradingInstructions;

    private Set<String> categories;

    private DifficultyLevel difficulty;

    private ExerciseMode mode;

    private Set<ExampleSubmission> exampleSubmissions;

    private String testRepositoryUrl;

    private Boolean allowOnlineEditor;

    private Boolean allowOfflineIde;

    private Boolean staticCodeAnalysisEnabled;

    private Integer maxStaticCodeAnalysisPenalty;

    private ProgrammingLanguage programmingLanguage;

    private String packageName;

    private Boolean sequentialTestRuns;

    private boolean showTestNamesToStudents;

    private Set<ProgrammingExerciseTestCase> testCases;

    private Set<StaticCodeAnalysisCategory> staticCodeAnalysisCategories;

    private ProjectType projectType;

    public ProgrammingExerciseExportDTO() {
    }

    public static ProgrammingExerciseExportDTO programmingExerciseExportDTOFromDomain(ProgrammingExercise programmingExercise) {
        var exerciseExportDto = new ProgrammingExerciseExportDTO();
        exerciseExportDto.setTitle(programmingExercise.getTitle());
        exerciseExportDto.setShortName(programmingExercise.getShortName());
        exerciseExportDto.setMaxPoints(programmingExercise.getMaxPoints());
        exerciseExportDto.setBonusPoints(programmingExercise.getBonusPoints());
        exerciseExportDto.setAssessmentType(programmingExercise.getAssessmentType());
        exerciseExportDto.setIncludedInOverallScore(programmingExercise.getIncludedInOverallScore());
        exerciseExportDto.setGradingInstructions(programmingExercise.getGradingInstructions());
        exerciseExportDto.setCategories(Collections.unmodifiableSet(programmingExercise.getCategories()));
        exerciseExportDto.setDifficulty(programmingExercise.getDifficulty());
        exerciseExportDto.setMode(programmingExercise.getMode());
        exerciseExportDto.setExampleSubmissions(Collections.unmodifiableSet(programmingExercise.getExampleSubmissions()));
        exerciseExportDto.setTestRepositoryUrl(programmingExercise.getTestRepositoryUrl());
        exerciseExportDto.setAllowOnlineEditor(programmingExercise.isAllowOnlineEditor());
        exerciseExportDto.setAllowOfflineIde(programmingExercise.isAllowOfflineIde());
        exerciseExportDto.setStaticCodeAnalysisEnabled(programmingExercise.isStaticCodeAnalysisEnabled());
        exerciseExportDto.setMaxStaticCodeAnalysisPenalty(programmingExercise.getMaxStaticCodeAnalysisPenalty());
        exerciseExportDto.setProgrammingLanguage(programmingExercise.getProgrammingLanguage());
        exerciseExportDto.setPackageName(programmingExercise.getPackageName());
        exerciseExportDto.setSequentialTestRuns(programmingExercise.hasSequentialTestRuns());
        exerciseExportDto.setShowTestNamesToStudents(programmingExercise.getShowTestNamesToStudents());
        exerciseExportDto.setTestCases(Collections.unmodifiableSet(programmingExercise.getTestCases()));
        exerciseExportDto.setStaticCodeAnalysisCategories(Collections.unmodifiableSet(programmingExercise.getStaticCodeAnalysisCategories()));
        exerciseExportDto.setProjectType(programmingExercise.getProjectType());
        return exerciseExportDto;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public Double getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(Double maxPoints) {
        this.maxPoints = maxPoints;
    }

    public Double getBonusPoints() {
        return bonusPoints;
    }

    public void setBonusPoints(Double bonusPoints) {
        this.bonusPoints = bonusPoints;
    }

    public AssessmentType getAssessmentType() {
        return assessmentType;
    }

    public void setAssessmentType(AssessmentType assessmentType) {
        this.assessmentType = assessmentType;
    }

    public IncludedInOverallScore getIncludedInOverallScore() {
        return includedInOverallScore;
    }

    public void setIncludedInOverallScore(IncludedInOverallScore includedInOverallScore) {
        this.includedInOverallScore = includedInOverallScore;
    }

    public String getGradingInstructions() {
        return gradingInstructions;
    }

    public void setGradingInstructions(String gradingInstructions) {
        this.gradingInstructions = gradingInstructions;
    }

    public Set<String> getCategories() {
        return categories;
    }

    public void setCategories(Set<String> categories) {
        this.categories = categories;
    }

    public DifficultyLevel getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(DifficultyLevel difficulty) {
        this.difficulty = difficulty;
    }

    public ExerciseMode getMode() {
        return mode;
    }

    public void setMode(ExerciseMode mode) {
        this.mode = mode;
    }

    public Set<ExampleSubmission> getExampleSubmissions() {
        return exampleSubmissions;
    }

    public void setExampleSubmissions(Set<ExampleSubmission> exampleSubmissions) {
        this.exampleSubmissions = exampleSubmissions;
    }

    public String getTestRepositoryUrl() {
        return testRepositoryUrl;
    }

    public void setTestRepositoryUrl(String testRepositoryUrl) {
        this.testRepositoryUrl = testRepositoryUrl;
    }

    public Boolean getAllowOnlineEditor() {
        return allowOnlineEditor;
    }

    public void setAllowOnlineEditor(Boolean allowOnlineEditor) {
        this.allowOnlineEditor = allowOnlineEditor;
    }

    public Boolean getAllowOfflineIde() {
        return allowOfflineIde;
    }

    public void setAllowOfflineIde(Boolean allowOfflineIde) {
        this.allowOfflineIde = allowOfflineIde;
    }

    public Boolean getStaticCodeAnalysisEnabled() {
        return staticCodeAnalysisEnabled;
    }

    public void setStaticCodeAnalysisEnabled(Boolean staticCodeAnalysisEnabled) {
        this.staticCodeAnalysisEnabled = staticCodeAnalysisEnabled;
    }

    public Integer getMaxStaticCodeAnalysisPenalty() {
        return maxStaticCodeAnalysisPenalty;
    }

    public void setMaxStaticCodeAnalysisPenalty(Integer maxStaticCodeAnalysisPenalty) {
        this.maxStaticCodeAnalysisPenalty = maxStaticCodeAnalysisPenalty;
    }

    public ProgrammingLanguage getProgrammingLanguage() {
        return programmingLanguage;
    }

    public void setProgrammingLanguage(ProgrammingLanguage programmingLanguage) {
        this.programmingLanguage = programmingLanguage;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public Boolean getSequentialTestRuns() {
        return sequentialTestRuns;
    }

    public void setSequentialTestRuns(Boolean sequentialTestRuns) {
        this.sequentialTestRuns = sequentialTestRuns;
    }

    public boolean isShowTestNamesToStudents() {
        return showTestNamesToStudents;
    }

    public void setShowTestNamesToStudents(boolean showTestNamesToStudents) {
        this.showTestNamesToStudents = showTestNamesToStudents;
    }

    public Set<ProgrammingExerciseTestCase> getTestCases() {
        return testCases;
    }

    public void setTestCases(Set<ProgrammingExerciseTestCase> testCases) {
        this.testCases = testCases;
    }

    public Set<StaticCodeAnalysisCategory> getStaticCodeAnalysisCategories() {
        return staticCodeAnalysisCategories;
    }

    public void setStaticCodeAnalysisCategories(Set<StaticCodeAnalysisCategory> staticCodeAnalysisCategories) {
        this.staticCodeAnalysisCategories = staticCodeAnalysisCategories;
    }

    public ProjectType getProjectType() {
        return projectType;
    }

    public void setProjectType(ProjectType projectType) {
        this.projectType = projectType;
    }
}
