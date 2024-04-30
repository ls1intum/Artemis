package de.tum.in.www1.artemis.domain.iris.settings;

import jakarta.annotation.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SecondaryTable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.iris.IrisTemplate;

/**
 * An {@link IrisSubSettings} implementation for code editor settings.
 * Code editor settings notably provide multiple {@link IrisTemplate}s for the different steps in the code generation.
 */
@Entity
@DiscriminatorValue("CODE_EDITOR")
@SecondaryTable(name = "iris_code_editor_sub_settings")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisCodeEditorSubSettings extends IrisSubSettings {

    @Nullable
    @JoinColumn(table = "iris_code_editor_sub_settings")
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private IrisTemplate chatTemplate;

    @Nullable
    @JoinColumn(table = "iris_code_editor_sub_settings")
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private IrisTemplate problemStatementGenerationTemplate;

    @Nullable
    @JoinColumn(table = "iris_code_editor_sub_settings")
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private IrisTemplate templateRepoGenerationTemplate;

    @Nullable
    @JoinColumn(table = "iris_code_editor_sub_settings")
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private IrisTemplate solutionRepoGenerationTemplate;

    @Nullable
    @JoinColumn(table = "iris_code_editor_sub_settings")
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private IrisTemplate testRepoGenerationTemplate;

    @Nullable
    public IrisTemplate getChatTemplate() {
        return chatTemplate;
    }

    public void setChatTemplate(@Nullable IrisTemplate chatTemplate) {
        this.chatTemplate = chatTemplate;
    }

    @Nullable
    public IrisTemplate getProblemStatementGenerationTemplate() {
        return problemStatementGenerationTemplate;
    }

    public void setProblemStatementGenerationTemplate(@Nullable IrisTemplate problemStatementGenerationTemplate) {
        this.problemStatementGenerationTemplate = problemStatementGenerationTemplate;
    }

    @Nullable
    public IrisTemplate getTemplateRepoGenerationTemplate() {
        return templateRepoGenerationTemplate;
    }

    public void setTemplateRepoGenerationTemplate(@Nullable IrisTemplate templateRepoGenerationTemplate) {
        this.templateRepoGenerationTemplate = templateRepoGenerationTemplate;
    }

    @Nullable
    public IrisTemplate getSolutionRepoGenerationTemplate() {
        return solutionRepoGenerationTemplate;
    }

    public void setSolutionRepoGenerationTemplate(@Nullable IrisTemplate solutionRepoGenerationTemplate) {
        this.solutionRepoGenerationTemplate = solutionRepoGenerationTemplate;
    }

    @Nullable
    public IrisTemplate getTestRepoGenerationTemplate() {
        return testRepoGenerationTemplate;
    }

    public void setTestRepoGenerationTemplate(@Nullable IrisTemplate testRepoGenerationTemplate) {
        this.testRepoGenerationTemplate = testRepoGenerationTemplate;
    }
}
