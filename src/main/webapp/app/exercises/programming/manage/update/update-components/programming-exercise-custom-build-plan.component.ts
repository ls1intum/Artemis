import { Component, Input, OnChanges, SimpleChanges, ViewChild } from '@angular/core';
import { BuildAction, PlatformAction, ProgrammingExercise, ProgrammingLanguage, ProjectType, ScriptAction, WindFile } from 'app/entities/programming-exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { FileService } from 'app/shared/http/file.service';

@Component({
    selector: 'jhi-programming-exercise-custom-build-plan',
    templateUrl: './programming-exercise-custom-build-plan.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseCustomBuildPlanComponent implements OnChanges {
    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;

    programmingLanguage?: ProgrammingLanguage;
    projectType?: ProjectType;
    staticCodeAnalysisEnabled?: boolean;
    sequentialTestRuns?: boolean;
    testwiseCoverageEnabled?: boolean;

    constructor(private fileService: FileService) {}

    code: string = '#!/bin/bash\n\n# Add your custom build plan here\n\nexit 0';
    active?: BuildAction = undefined;

    private _editor?: AceEditorComponent;

    @ViewChild('editor', { static: false }) set editor(value: AceEditorComponent) {
        this._editor = value;
        if (this._editor) {
            this.setupEditor();
            this._editor.setText(this.code);
        }
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.programmingExerciseCreationConfig || changes.programmingExercise) {
            if (this.shouldReloadTemplate()) {
                this.loadAeolusTemplate();
            }
        }
    }

    shouldReloadTemplate(): boolean {
        return (
            this.programmingExercise.programmingLanguage !== this.programmingLanguage ||
            this.programmingExercise.projectType !== this.projectType ||
            this.programmingExercise.staticCodeAnalysisEnabled !== this.staticCodeAnalysisEnabled ||
            this.programmingExercise.sequentialTestRuns !== this.sequentialTestRuns ||
            this.programmingExercise.testwiseCoverageEnabled !== this.testwiseCoverageEnabled
        );
    }

    /**
     * In case the programming language or project type changes, we need to reset the template and the build plan
     * @private
     */
    resetCustomBuildPlan() {
        this.programmingExercise.windFile = undefined;
        this.programmingExercise.buildPlanConfiguration = undefined;
    }
    /**
     * Loads the predefined template for the selected programming language and project type
     * if there is one available.
     * @private
     */
    loadAeolusTemplate() {
        this.resetCustomBuildPlan();
        if (!this.programmingExercise.programmingLanguage) {
            return;
        }
        this.programmingLanguage = this.programmingExercise.programmingLanguage;
        this.projectType = this.programmingExercise.projectType;
        this.staticCodeAnalysisEnabled = this.programmingExercise.staticCodeAnalysisEnabled;
        this.sequentialTestRuns = this.programmingExercise.sequentialTestRuns;
        this.testwiseCoverageEnabled = this.programmingExercise.testwiseCoverageEnabled;
        this.fileService
            .getAeolusTemplateFile(this.programmingLanguage, this.projectType, this.staticCodeAnalysisEnabled, this.sequentialTestRuns, this.testwiseCoverageEnabled)
            .subscribe({
                next: (file) => {
                    if (file && !this.programmingExerciseCreationConfig.buildPlanLoaded) {
                        this.programmingExerciseCreationConfig.buildPlanLoaded = true;
                        const templateFile: WindFile = JSON.parse(file);
                        const actions: BuildAction[] = [];
                        templateFile.actions.forEach((anyAction: any) => {
                            let action: BuildAction | undefined;
                            if (anyAction.class === 'script-action' || anyAction.script) {
                                action = new ScriptAction();
                                (action as ScriptAction).script = anyAction.script;
                            } else {
                                action = new PlatformAction();
                                (action as PlatformAction).kind = anyAction.kind;
                                (action as PlatformAction).parameters = anyAction.parameters;
                            }
                            action.name = anyAction.name;
                            action.runAlways = anyAction.runAlways;
                            actions.push(action);
                        });
                        templateFile.actions = actions;
                        this.programmingExercise.windFile = templateFile;
                    }
                },
                error: () => {
                    this.resetCustomBuildPlan();
                    this.programmingExerciseCreationConfig.buildPlanLoaded = true;
                },
            });
    }

    get editor(): AceEditorComponent | undefined {
        return this._editor;
    }

    faQuestionCircle = faQuestionCircle;

    protected getActionScript(action: string): string {
        const foundAction: BuildAction | undefined = this.programmingExercise.windFile?.actions.find((a) => a.name === action);
        if (foundAction && foundAction instanceof ScriptAction) {
            return (foundAction as ScriptAction).script;
        }
        return '';
    }

    isScriptAction(action: BuildAction): boolean {
        return action instanceof ScriptAction;
    }

    changeActiveAction(action: string): void {
        if (!this.programmingExercise.windFile) {
            return;
        }

        this.code = this.getActionScript(action);
        this.active = this.programmingExercise.windFile.actions.find((a) => a.name === action);
        if (this.needsEditor() && this.editor) {
            this.editor.setText(this.code);
        }
    }

    protected needsEditor(): boolean {
        return this.active instanceof ScriptAction;
    }

    deleteAction(action: string): void {
        if (this.programmingExercise.windFile) {
            this.programmingExercise.windFile.actions = this.programmingExercise.windFile.actions.filter((a) => a.name !== action);
            if (this.active?.name === action) {
                this.active = undefined;
                this.code = '';
            }
        }
    }

    addAction(action: string): void {
        if (this.programmingExercise.windFile) {
            const newAction = new ScriptAction();
            newAction.script = '#!/bin/bash\n\n# Add your custom build plan action here\n\nexit 0';
            newAction.name = action;
            newAction.runAlways = false;
            this.programmingExercise.windFile.actions.push(newAction);
            this.changeActiveAction(action);
        }
    }

    codeChanged(code: string): void {
        if (this.active instanceof ScriptAction) {
            (this.active as ScriptAction).script = code;
        }
    }

    /**
     * Sets up an ace editor for the template or solution file.
     */
    setupEditor(): void {
        if (!this._editor) {
            return;
        }
        this._editor.getEditor().setOptions({
            animatedScroll: true,
            maxLines: 20,
            showPrintMargin: false,
            readOnly: false,
            highlightActiveLine: false,
            highlightGutterLine: false,
            minLines: 20,
            mode: 'ace/mode/sh',
        });
        this._editor.getEditor().renderer.setOptions({
            showFoldWidgets: false,
        });
    }
}
