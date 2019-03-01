package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.*;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

/**
 * A ProgrammingExercise.
 */
@Entity
@DiscriminatorValue(value="P")
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

    @OneToOne(orphanRemoval=true)
    @JoinColumn(unique = true)
    @JsonIgnoreProperties("exercise")
    private Participation templateParticipation;

    @OneToOne
    @JoinColumn(unique = true)
    @JsonIgnoreProperties("exercise")
    private Participation solutionParticipation;

    // jhipster-needle-entity-add-field - Jhipster will add fields here, do not remove
    @JsonIgnore // we now store it in templateParticipation --> this is just a convenience getter
    public String getTemplateRepositoryUrl() {
        if (templateParticipation != null) {
            return templateParticipation.getRepositoryUrl();
        }
        return null;
    }

    public ProgrammingExercise templateRepositoryUrl(String templateRepositoryUrl) {
        this.templateParticipation.setRepositoryUrl(templateRepositoryUrl);
        return this;
    }

    public void setTemplateRepositoryUrl(String templateRepositoryUrl) {
        this.templateParticipation.setRepositoryUrl(templateRepositoryUrl);
    }

    @JsonIgnore // we now store it in solutionParticipation --> this is just a convenience getter
    public String getSolutionRepositoryUrl() {
        if (solutionParticipation!= null) {
            return solutionParticipation.getRepositoryUrl();
        }
        return null;
    }

    public ProgrammingExercise solutionRepositoryUrl(String solutionRepositoryUrl) {
        this.solutionParticipation.setRepositoryUrl(solutionRepositoryUrl);
        return this;
    }

    public void setSolutionRepositoryUrl(String solutionRepositoryUrl) {
        this.solutionParticipation.setRepositoryUrl(solutionRepositoryUrl);
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
        if (templateParticipation != null) {
            return templateParticipation.getBuildPlanId();
        }
        return null;
    }

    public ProgrammingExercise templateBuildPlanId(String templateBuildPlanId) {
        this.templateParticipation.setBuildPlanId(templateBuildPlanId);
        return this;
    }

    public void setTemplateBuildPlanId(String templateBuildPlanId) {
        this.templateParticipation.setBuildPlanId(templateBuildPlanId);
    }

    @JsonIgnore // we now store it in solutionParticipation --> this is just a convenience getter
    public String getSolutionBuildPlanId() {
        if (solutionParticipation != null) {
            return solutionParticipation.getBuildPlanId();
        }
        return null;
    }

    public ProgrammingExercise solutionBuildPlanId(String solutionBuildPlanId) {
        this.solutionParticipation.setBuildPlanId(solutionBuildPlanId);
        return this;
    }

    public void setSolutionBuildPlanId(String solutionBuildPlanId) {
        this.solutionParticipation.setBuildPlanId(solutionBuildPlanId);
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
        } catch (MalformedURLException e) {
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
        } catch (MalformedURLException e) {
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
        } catch (MalformedURLException e) {
            log.warn("Cannot create URL for testRepositoryUrl: " + testRepositoryUrl + " due to the following error: " + e.getMessage());
        }
        return null;
    }

    @JsonIgnore
    public String getProjectKey() {
        //this is the key used for Bitbucket and Bamboo
        //remove all whitespace and make sure it is upper case
        return (this.getCourse().getShortName() + this.getShortName()).toUpperCase().replaceAll("\\s+","");
    }

    @JsonIgnore
    public String getProjectName() {
        //this is the name used for Bitbucket and Bamboo
        return this.getCourse().getShortName() + " " + this.getTitle();
    }

    @JsonIgnore
    public String getPackageFolderName() {
        return getPackageName().replace(".", "/");
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
        return "ProgrammingExercise{" +
            "id=" + getId() +
            ", templateRepositoryUrl='" + getTemplateRepositoryUrl() + "'" +
            ", solutionRepositoryUrl='" + getSolutionRepositoryUrl() + "'" +
            ", templateBuildPlanId='" + getTemplateBuildPlanId() + "'" +
            ", solutionBuildPlanId='" + getSolutionBuildPlanId() + "'" +
            ", publishBuildPlanUrl='" + isPublishBuildPlanUrl() + "'" +
            ", allowOnlineEditor='" + isAllowOnlineEditor() + "'" +
            ", programmingLanguage='" + getProgrammingLanguage() + "'" +
            ", packageName='" + getPackageName() + "'" +
            "}";
    }


    //TODO: Please note: The following code will be removed soon as it is deprecated and only exists for migration purposes

    @Column(name = "base_repository_url")
    @Deprecated
    private String templateRepositoryUrlOld;

    @Column(name = "solution_repository_url")
    @Deprecated
    private String solutionRepositoryUrlOld;

    @Column(name = "base_build_plan_id")
    @Deprecated
    private String templateBuildPlanIdOld;

    @Column(name = "solution_build_plan_id")
    @Deprecated
    private String solutionBuildPlanIdOld;

    @Deprecated
    @JsonIgnore
    public String getTemplateRepositoryUrlOld() {
        return templateRepositoryUrlOld;
    }

    @Deprecated
    @JsonIgnore
    public String getSolutionRepositoryUrlOld() {
        return solutionRepositoryUrlOld;
    }

    @Deprecated
    @JsonIgnore
    public String getTemplateBuildPlanIdOld() {
        return templateBuildPlanIdOld;
    }

    @Deprecated
    @JsonIgnore
    public String getSolutionBuildPlanIdOld() {
        return solutionBuildPlanIdOld;
    }

    @Deprecated
    public void setTemplateRepositoryUrlOld(String templateRepositoryUrlOld) {
        this.templateRepositoryUrlOld = templateRepositoryUrlOld;
    }

    @Deprecated
    public void setSolutionRepositoryUrlOld(String solutionRepositoryUrlOld) {
        this.solutionRepositoryUrlOld = solutionRepositoryUrlOld;
    }

    @Deprecated
    public void setTemplateBuildPlanIdOld(String templateBuildPlanIdOld) {
        this.templateBuildPlanIdOld = templateBuildPlanIdOld;
    }

    @Deprecated
    public void setSolutionBuildPlanIdOld(String solutionBuildPlanIdOld) {
        this.solutionBuildPlanIdOld = solutionBuildPlanIdOld;
    }

}
