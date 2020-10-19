package de.tum.in.www1.artemis.service.programming;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

public class ProgrammingLanguageFeature {

    private ProgrammingLanguage programmingLanguage;

    private boolean sequentialTestRuns;

    private boolean staticCodeAnalysis;

    private boolean bambooBuildSupported;

    private boolean jenkinsBuildSupported;

    private boolean plagiarismCheckSupported;

    private boolean packageNameRequired;

    public ProgrammingLanguageFeature(ProgrammingLanguage programmingLanguage, boolean sequentialTestRuns, boolean staticCodeAnalysis, boolean bambooBuildSupported,
            boolean jenkinsBuildSupported, boolean plagiarismCheckSupported, boolean packageNameRequired) {
        this.programmingLanguage = programmingLanguage;
        this.sequentialTestRuns = sequentialTestRuns;
        this.staticCodeAnalysis = staticCodeAnalysis;
        this.bambooBuildSupported = bambooBuildSupported;
        this.jenkinsBuildSupported = jenkinsBuildSupported;
        this.plagiarismCheckSupported = plagiarismCheckSupported;
        this.packageNameRequired = packageNameRequired;
    }

    public ProgrammingLanguage getProgrammingLanguage() {
        return programmingLanguage;
    }

    public void setProgrammingLanguage(ProgrammingLanguage programmingLanguage) {
        this.programmingLanguage = programmingLanguage;
    }

    public boolean isSequentialTestRuns() {
        return sequentialTestRuns;
    }

    public void setSequentialTestRuns(boolean sequentialTestRuns) {
        this.sequentialTestRuns = sequentialTestRuns;
    }

    public boolean isStaticCodeAnalysis() {
        return staticCodeAnalysis;
    }

    public void setStaticCodeAnalysis(boolean staticCodeAnalysis) {
        this.staticCodeAnalysis = staticCodeAnalysis;
    }

    public boolean isBambooBuildSupported() {
        return bambooBuildSupported;
    }

    public void setBambooBuildSupported(boolean bambooBuildSupported) {
        this.bambooBuildSupported = bambooBuildSupported;
    }

    public boolean isJenkinsBuildSupported() {
        return jenkinsBuildSupported;
    }

    public void setJenkinsBuildSupported(boolean jenkinsBuildSupported) {
        this.jenkinsBuildSupported = jenkinsBuildSupported;
    }

    public boolean isPlagiarismCheckSupported() {
        return plagiarismCheckSupported;
    }

    public void setPlagiarismCheckSupported(boolean plagiarismCheckSupported) {
        this.plagiarismCheckSupported = plagiarismCheckSupported;
    }

    public boolean isPackageNameRequired() {
        return packageNameRequired;
    }

    public void setPackageNameRequired(boolean packageNameRequired) {
        this.packageNameRequired = packageNameRequired;
    }
}
