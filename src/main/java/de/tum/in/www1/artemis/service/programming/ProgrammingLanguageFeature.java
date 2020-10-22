package de.tum.in.www1.artemis.service.programming;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

/**
 * Store configuration of a specific programming language.
 * The configuration is also available in the client as the {@link ProgrammingLanguageFeatureContributor} exposes them.
 */
public class ProgrammingLanguageFeature {

    private ProgrammingLanguage programmingLanguage;

    private boolean sequentialTestRuns;

    private boolean staticCodeAnalysis;

    private boolean plagiarismCheckSupported;

    private boolean packageNameRequired;

    private boolean checkoutSolutionRepositoryAllowed;

    public ProgrammingLanguageFeature(ProgrammingLanguage programmingLanguage, boolean sequentialTestRuns, boolean staticCodeAnalysis, boolean plagiarismCheckSupported,
            boolean packageNameRequired, boolean checkoutSolutionRepositoryAllowed) {
        this.programmingLanguage = programmingLanguage;
        this.sequentialTestRuns = sequentialTestRuns;
        this.staticCodeAnalysis = staticCodeAnalysis;
        this.plagiarismCheckSupported = plagiarismCheckSupported;
        this.packageNameRequired = packageNameRequired;
        this.checkoutSolutionRepositoryAllowed = checkoutSolutionRepositoryAllowed;
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

    public boolean isCheckoutSolutionRepositoryAllowed() {
        return checkoutSolutionRepositoryAllowed;
    }

    public void setCheckoutSolutionRepositoryAllowed(boolean checkoutSolutionRepositoryAllowed) {
        this.checkoutSolutionRepositoryAllowed = checkoutSolutionRepositoryAllowed;
    }
}
