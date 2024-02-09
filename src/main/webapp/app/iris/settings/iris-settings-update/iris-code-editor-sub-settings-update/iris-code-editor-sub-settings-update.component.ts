import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { IrisTemplate } from 'app/entities/iris/settings/iris-template';
import { IrisCodeEditorSubSettings, IrisSubSettings } from 'app/entities/iris/settings/iris-sub-settings.model';

@Component({
    selector: 'jhi-iris-code-editor-sub-settings-update',
    templateUrl: './iris-code-editor-sub-settings-update.component.html',
})
export class IrisCodeEditorSubSettingsUpdateComponent implements OnInit, OnChanges {
    @Input()
    subSettings?: IrisCodeEditorSubSettings;

    @Input()
    parentSubSettings?: IrisCodeEditorSubSettings;

    @Output()
    onChanges = new EventEmitter<IrisSubSettings>();

    previousChatTemplate?: IrisTemplate;
    previousProblemStatementGenerationTemplate?: IrisTemplate;
    previousTemplateRepoGenerationTemplate?: IrisTemplate;
    previousSolutionRepoGenerationTemplate?: IrisTemplate;
    previousTestRepoGenerationTemplate?: IrisTemplate;

    chatTemplateContent: string;
    problemStatementGenerationTemplateContent: string;
    templateRepoGenerationTemplateContent: string;
    solutionRepoGenerationTemplateContent: string;
    testRepoGenerationTemplateContent: string;

    ngOnInit(): void {
        this.resetTemplates();
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.subSettings || changes.parentSubSettings) {
            this.resetTemplates();
        }
    }

    private resetTemplates() {
        this.chatTemplateContent = this.subSettings?.chatTemplate?.content ?? this.parentSubSettings?.chatTemplate?.content ?? '';
        this.problemStatementGenerationTemplateContent =
            this.subSettings?.problemStatementGenerationTemplate?.content ?? this.parentSubSettings?.problemStatementGenerationTemplate?.content ?? '';
        this.templateRepoGenerationTemplateContent =
            this.subSettings?.templateRepoGenerationTemplate?.content ?? this.parentSubSettings?.templateRepoGenerationTemplate?.content ?? '';
        this.solutionRepoGenerationTemplateContent =
            this.subSettings?.solutionRepoGenerationTemplate?.content ?? this.parentSubSettings?.solutionRepoGenerationTemplate?.content ?? '';
        this.testRepoGenerationTemplateContent = this.subSettings?.testRepoGenerationTemplate?.content ?? this.parentSubSettings?.testRepoGenerationTemplate?.content ?? '';
    }

    onInheritTemplateChanged() {
        if (this.subSettings?.chatTemplate) {
            this.previousChatTemplate = this.subSettings?.chatTemplate;
            this.subSettings.chatTemplate = undefined;
            this.chatTemplateContent = this.parentSubSettings?.chatTemplate?.content ?? '';
        } else {
            const irisTemplate = new IrisTemplate();
            irisTemplate.content = '';
            this.subSettings!.chatTemplate = this.previousChatTemplate ?? irisTemplate;
        }
        if (this.subSettings?.problemStatementGenerationTemplate) {
            this.previousProblemStatementGenerationTemplate = this.subSettings?.problemStatementGenerationTemplate;
            this.subSettings.problemStatementGenerationTemplate = undefined;
            this.problemStatementGenerationTemplateContent = this.parentSubSettings?.problemStatementGenerationTemplate?.content ?? '';
        } else {
            const irisTemplate = new IrisTemplate();
            irisTemplate.content = '';
            this.subSettings!.problemStatementGenerationTemplate = this.previousProblemStatementGenerationTemplate ?? irisTemplate;
        }
        if (this.subSettings?.templateRepoGenerationTemplate) {
            this.previousTemplateRepoGenerationTemplate = this.subSettings?.templateRepoGenerationTemplate;
            this.subSettings.templateRepoGenerationTemplate = undefined;
            this.templateRepoGenerationTemplateContent = this.parentSubSettings?.templateRepoGenerationTemplate?.content ?? '';
        } else {
            const irisTemplate = new IrisTemplate();
            irisTemplate.content = '';
            this.subSettings!.templateRepoGenerationTemplate = this.previousTemplateRepoGenerationTemplate ?? irisTemplate;
        }
        if (this.subSettings?.solutionRepoGenerationTemplate) {
            this.previousSolutionRepoGenerationTemplate = this.subSettings?.solutionRepoGenerationTemplate;
            this.subSettings.solutionRepoGenerationTemplate = undefined;
            this.solutionRepoGenerationTemplateContent = this.parentSubSettings?.solutionRepoGenerationTemplate?.content ?? '';
        } else {
            const irisTemplate = new IrisTemplate();
            irisTemplate.content = '';
            this.subSettings!.solutionRepoGenerationTemplate = this.previousSolutionRepoGenerationTemplate ?? irisTemplate;
        }
        if (this.subSettings?.testRepoGenerationTemplate) {
            this.previousTestRepoGenerationTemplate = this.subSettings?.testRepoGenerationTemplate;
            this.subSettings.testRepoGenerationTemplate = undefined;
            this.testRepoGenerationTemplateContent = this.parentSubSettings?.testRepoGenerationTemplate?.content ?? '';
        } else {
            const irisTemplate = new IrisTemplate();
            irisTemplate.content = '';
            this.subSettings!.testRepoGenerationTemplate = this.previousTestRepoGenerationTemplate ?? irisTemplate;
        }
    }

    onTemplateChanged() {
        if (this.subSettings?.chatTemplate) {
            this.subSettings.chatTemplate.content = this.chatTemplateContent;
        } else {
            const irisTemplate = new IrisTemplate();
            irisTemplate.content = this.chatTemplateContent;
            this.subSettings!.chatTemplate = irisTemplate;
        }
        if (this.subSettings?.problemStatementGenerationTemplate) {
            this.subSettings.problemStatementGenerationTemplate.content = this.problemStatementGenerationTemplateContent;
        } else {
            const irisTemplate = new IrisTemplate();
            irisTemplate.content = this.problemStatementGenerationTemplateContent;
            this.subSettings!.problemStatementGenerationTemplate = irisTemplate;
        }
        if (this.subSettings?.templateRepoGenerationTemplate) {
            this.subSettings.templateRepoGenerationTemplate.content = this.templateRepoGenerationTemplateContent;
        } else {
            const irisTemplate = new IrisTemplate();
            irisTemplate.content = this.templateRepoGenerationTemplateContent;
            this.subSettings!.templateRepoGenerationTemplate = irisTemplate;
        }
        if (this.subSettings?.solutionRepoGenerationTemplate) {
            this.subSettings.solutionRepoGenerationTemplate.content = this.solutionRepoGenerationTemplateContent;
        } else {
            const irisTemplate = new IrisTemplate();
            irisTemplate.content = this.solutionRepoGenerationTemplateContent;
            this.subSettings!.solutionRepoGenerationTemplate = irisTemplate;
        }
        if (this.subSettings?.testRepoGenerationTemplate) {
            this.subSettings.testRepoGenerationTemplate.content = this.testRepoGenerationTemplateContent;
        } else {
            const irisTemplate = new IrisTemplate();
            irisTemplate.content = this.testRepoGenerationTemplateContent;
            this.subSettings!.testRepoGenerationTemplate = irisTemplate;
        }
    }
}
