package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import javax.persistence.*;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

/**
 * A ProgrammingExercise.
 */
@Entity
@DiscriminatorValue(value = "P")
public class ProgrammingExercise extends Exercise implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExercise.class);

    private static final long serialVersionUID = 1L;

    @Column(name = "test_repository_url")
    private String testRepositoryUrl;

    @Column(name = "publish_build_plan_url")
    private Boolean publishBuildPlanUrl;

    @Column(name = "allow_online_editor")
    private Boolean allowOnlineEditor;

    @Enumerated(EnumType.STRING)
    @Column(name = "programming_language")
    private ProgrammingLanguage programmingLanguage;

    @Column(name = "package_name")
    private String packageName;

    @OneToOne(cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(unique = true)
    @JsonIgnoreProperties("exercise")
    private Participation templateParticipation;

    @OneToOne(cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(unique = true)
    @JsonIgnoreProperties("exercise")
    private Participation solutionParticipation;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("exercise")
    private Set<ProgrammingExerciseTestCase> testCases = new HashSet<>();

    // jhipster-needle-entity-add-field - Jhipster will add fields here, do not remove
    @JsonIgnore // we now store it in templateParticipation --> this is just a convenience getter
    public String getTemplateRepositoryUrl() {
        if (templateParticipation != null && Hibernate.isInitialized(templateParticipation)) {
            return templateParticipation.getRepositoryUrl();
        }
        return null;
    }

    private void setTemplateRepositoryUrl(String templateRepositoryUrl) {
        if (templateParticipation != null && Hibernate.isInitialized(templateParticipation)) {
            this.templateParticipation.setRepositoryUrl(templateRepositoryUrl);
        }
    }

    @JsonIgnore // we now store it in solutionParticipation --> this is just a convenience getter
    public String getSolutionRepositoryUrl() {
        if (solutionParticipation != null && Hibernate.isInitialized(solutionParticipation)) {
            return solutionParticipation.getRepositoryUrl();
        }
        return null;
    }

    private void setSolutionRepositoryUrl(String solutionRepositoryUrl) {
        if (solutionParticipation != null && Hibernate.isInitialized(solutionParticipation)) {
            this.solutionParticipation.setRepositoryUrl(solutionRepositoryUrl);
        }
    }

    public void setTestRepositoryUrl(String testRepositoryUrl) {
        this.testRepositoryUrl = testRepositoryUrl;
    }

    public String getTestRepositoryUrl() {
        return testRepositoryUrl;
    }

    public ProgrammingExercise testRepositoryUrl(String testRepositoryUrl) {
        this.testRepositoryUrl = testRepositoryUrl;
        return this;
    }

    @JsonIgnore // we now store it in templateParticipation --> this is just a convenience getter
    public String getTemplateBuildPlanId() {
        if (templateParticipation != null && Hibernate.isInitialized(templateParticipation)) {
            return templateParticipation.getBuildPlanId();
        }
        return null;
    }

    private void setTemplateBuildPlanId(String templateBuildPlanId) {
        if (templateParticipation != null && Hibernate.isInitialized(templateParticipation)) {
            this.templateParticipation.setBuildPlanId(templateBuildPlanId);
        }
    }

    @JsonIgnore // we now store it in solutionParticipation --> this is just a convenience getter
    public String getSolutionBuildPlanId() {
        if (solutionParticipation != null && Hibernate.isInitialized(solutionParticipation)) {
            return solutionParticipation.getBuildPlanId();
        }
        return null;
    }

    private void setSolutionBuildPlanId(String solutionBuildPlanId) {
        if (solutionParticipation != null && Hibernate.isInitialized(solutionParticipation)) {
            this.solutionParticipation.setBuildPlanId(solutionBuildPlanId);
        }
    }

    public Boolean isPublishBuildPlanUrl() {
        return publishBuildPlanUrl;
    }

    public ProgrammingExercise publishBuildPlanUrl(Boolean publishBuildPlanUrl) {
        this.publishBuildPlanUrl = publishBuildPlanUrl;
        return this;
    }

    public void setPublishBuildPlanUrl(Boolean publishBuildPlanUrl) {
        this.publishBuildPlanUrl = publishBuildPlanUrl;
    }

    public Boolean isAllowOnlineEditor() {
        return allowOnlineEditor;
    }

    public ProgrammingExercise allowOnlineEditor(Boolean allowOnlineEditor) {
        this.allowOnlineEditor = allowOnlineEditor;
        return this;
    }

    public void setAllowOnlineEditor(Boolean allowOnlineEditor) {
        this.allowOnlineEditor = allowOnlineEditor;
    }

    public ProgrammingLanguage getProgrammingLanguage() {
        return programmingLanguage;
    }

    public ProgrammingExercise programmingLanguage(ProgrammingLanguage programmingLanguage) {
        this.programmingLanguage = programmingLanguage;
        return this;
    }

    public void setProgrammingLanguage(ProgrammingLanguage programmingLanguage) {
        this.programmingLanguage = programmingLanguage;
    }

    public String getPackageName() {
        return packageName;
    }

    public ProgrammingExercise packageName(String packageName) {
        this.packageName = packageName;
        return this;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public Participation getTemplateParticipation() {
        return templateParticipation;
    }

    public void setTemplateParticipation(Participation templateParticipation) {
        this.templateParticipation = templateParticipation;
    }

    public Participation getSolutionParticipation() {
        return solutionParticipation;
    }

    public void setSolutionParticipation(Participation solutionParticipation) {
        this.solutionParticipation = solutionParticipation;
    }

    // jhipster-needle-entity-add-getters-setters - Jhipster will add getters and setters here, do not remove

    @JsonIgnore
    public URL getTemplateRepositoryUrlAsUrl() {
        String templateRepositoryUrl = getTemplateRepositoryUrl();
        if (templateRepositoryUrl == null || templateRepositoryUrl.isEmpty()) {
            return null;
        }
        try {
            return new URL(templateRepositoryUrl);
        }
        catch (MalformedURLException e) {
            log.warn("Cannot create URL for templateRepositoryUrl: " + templateRepositoryUrl + " due to the following error: " + e.getMessage());
        }
        return null;
    }

    @JsonIgnore
    public URL getSolutionRepositoryUrlAsUrl() {
        String solutionRepositoryUrl = getSolutionRepositoryUrl();
        if (solutionRepositoryUrl == null || solutionRepositoryUrl.isEmpty()) {
            return null;
        }
        try {
            return new URL(solutionRepositoryUrl);
        }
        catch (MalformedURLException e) {
            log.warn("Cannot create URL for solutionRepositoryUrl: " + solutionRepositoryUrl + " due to the following error: " + e.getMessage());
        }
        return null;
    }

    @JsonIgnore
    public URL getTestRepositoryUrlAsUrl() {
        if (testRepositoryUrl == null || testRepositoryUrl.isEmpty()) {
            return null;
        }
        try {
            return new URL(testRepositoryUrl);
        }
        catch (MalformedURLException e) {
            log.warn("Cannot create URL for testRepositoryUrl: " + testRepositoryUrl + " due to the following error: " + e.getMessage());
        }
        return null;
    }

    @JsonIgnore
    public String getProjectKey() {
        // this is the key used for Bitbucket and Bamboo
        // remove all whitespace and make sure it is upper case
        return (this.getCourse().getShortName() + this.getShortName()).toUpperCase().replaceAll("\\s+", "");
    }

    @JsonIgnore
    public String getProjectName() {
        // this is the name used for Bitbucket and Bamboo
        return this.getCourse().getShortName() + " " + this.getTitle();
    }

    @JsonIgnore
    public String getPackageFolderName() {
        return getPackageName().replace(".", "/");
    }

    public Set<ProgrammingExerciseTestCase> getTestCases() {
        return testCases;
    }

    public void setTestCases(Set<ProgrammingExerciseTestCase> testCases) {
        this.testCases = testCases;
    }

    /**
     * set all sensitive information to null, so no info with respect to the solution gets leaked to students through json
     */
    @Override
    public void filterSensitiveInformation() {
        setTemplateRepositoryUrl(null);
        setSolutionRepositoryUrl(null);
        setTestRepositoryUrl(null);
        setTemplateBuildPlanId(null);
        setSolutionBuildPlanId(null);
        super.filterSensitiveInformation();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProgrammingExercise programmingExercise = (ProgrammingExercise) o;
        if (programmingExercise.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), programmingExercise.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ProgrammingExercise{" + "id=" + getId() + ", templateRepositoryUrl='" + getTemplateRepositoryUrl() + "'" + ", solutionRepositoryUrl='" + getSolutionRepositoryUrl()
                + "'" + ", templateBuildPlanId='" + getTemplateBuildPlanId() + "'" + ", solutionBuildPlanId='" + getSolutionBuildPlanId() + "'" + ", publishBuildPlanUrl='"
                + isPublishBuildPlanUrl() + "'" + ", allowOnlineEditor='" + isAllowOnlineEditor() + "'" + ", programmingLanguage='" + getProgrammingLanguage() + "'"
                + ", packageName='" + getPackageName() + "'" + "}";
    }
}
