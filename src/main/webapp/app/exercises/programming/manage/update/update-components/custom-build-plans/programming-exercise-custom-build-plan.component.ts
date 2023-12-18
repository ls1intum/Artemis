import { Component, Input, OnChanges, SimpleChanges, ViewChild } from '@angular/core';
import { BuildAction, PlatformAction, ProgrammingExercise, ProgrammingLanguage, ProjectType, ScriptAction, WindFile } from 'app/entities/programming-exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { AeolusService } from 'app/exercises/programming/shared/service/aeolus.service';

@Component({
    selector: 'jhi-programming-exercise-custom-build-plan',
    templateUrl: './programming-exercise-custom-build-plan.component.html',
    styleUrls: ['../../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseCustomBuildPlanComponent implements OnChanges {
    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;

    programmingLanguage?: ProgrammingLanguage;
    projectType?: ProjectType;
    staticCodeAnalysisEnabled?: boolean;
    sequentialTestRuns?: boolean;
    testwiseCoverageEnabled?: boolean;

    constructor(private aeolusService: AeolusService) {}

    code: string = '#!/bin/bash\n\n# Add your custom build plan action here';
    private _editor?: AceEditorComponent;

    @ViewChild('editor', { static: false }) set editor(value: AceEditorComponent) {
        this._editor = value;
        if (this._editor) {
            this.setupEditor();
            if (this.programmingExercise.id) {
                this.code = this.programmingExercise.buildScript || '';
            }
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
        this.programmingExercise.buildScript = undefined;
    }
    /**
     * Loads the predefined template for the selected programming language and project type
     * if there is one available.
     * @private
     */
    loadAeolusTemplate() {
        if (this.programmingExercise.id) {
            return; // do not load template for existing exercises
        }
        this.resetCustomBuildPlan();
        if (!this.programmingExercise.programmingLanguage) {
            return;
        }
        this.programmingLanguage = this.programmingExercise.programmingLanguage;
        this.projectType = this.programmingExercise.projectType;
        this.staticCodeAnalysisEnabled = this.programmingExercise.staticCodeAnalysisEnabled;
        this.sequentialTestRuns = this.programmingExercise.sequentialTestRuns;
        this.testwiseCoverageEnabled = this.programmingExercise.testwiseCoverageEnabled;
        if (this.programmingExerciseCreationConfig.customBuildPlansSupported) {
            this.aeolusService
                .getAeolusTemplateFile(this.programmingLanguage, this.projectType, this.staticCodeAnalysisEnabled, this.sequentialTestRuns, this.testwiseCoverageEnabled)
                .subscribe({
                    next: (file) => {
                        if (file && !this.programmingExerciseCreationConfig.buildPlanLoaded) {
                            this.programmingExerciseCreationConfig.buildPlanLoaded = true;
                            const templateFile: WindFile = JSON.parse(file);
                            const windFile: WindFile = Object.assign(new WindFile(), templateFile);
                            const actions: BuildAction[] = [];
                            templateFile.actions.forEach((anyAction: any) => {
                                let action: BuildAction | undefined = undefined;
                                if (anyAction.script) {
                                    action = Object.assign(new ScriptAction(), anyAction);
                                } else {
                                    action = Object.assign(new PlatformAction(), anyAction);
                                }
                                if (!action) {
                                    return;
                                }
                                action.parameters = new Map<string, string | boolean | number>();
                                if (anyAction.parameters) {
                                    for (const key of Object.keys(anyAction.parameters)) {
                                        action.parameters.set(key, anyAction.parameters[key]);
                                    }
                                }
                                actions.push(action);
                            });
                            // somehow, the returned content has a scriptActions field, which is not defined in the WindFile class
                            delete windFile['scriptActions'];
                            windFile.actions = actions;
                            this.programmingExercise.windFile = windFile;
                        }
                    },
                    error: () => {
                        this.resetCustomBuildPlan();
                        this.programmingExerciseCreationConfig.buildPlanLoaded = true;
                    },
                });
            this.aeolusService
                .getAeolusTemplateScript(this.programmingLanguage, this.projectType, this.staticCodeAnalysisEnabled, this.sequentialTestRuns, this.testwiseCoverageEnabled)
                .subscribe({
                    next: (file) => {
                        if (file) {
                            this.programmingExerciseCreationConfig.buildPlanLoaded = true;
                            this.programmingExercise.buildScript = file;
                            this.code = file;
                        }
                    },
                    error: () => {
                        this.resetCustomBuildPlan();
                        this.programmingExerciseCreationConfig.buildPlanLoaded = true;
                    },
                });
        }
    }

    get editor(): AceEditorComponent | undefined {
        return this._editor;
    }

    faQuestionCircle = faQuestionCircle;

    codeChanged(code: string): void {
        this.code = code;
        this.programmingExercise.buildScript = code;
    }

    /**
     * Sets up an ace editor for the build plan script
     */
    setupEditor(): void {
        if (!this._editor) {
            return;
        }
        this._editor.getEditor().setOptions({
            animatedScroll: true,
            maxLines: 30,
            showPrintMargin: false,
            readOnly: false,
            highlightActiveLine: false,
            highlightGutterLine: false,
            minLines: 30,
            mode: 'ace/mode/sh',
        });
        this._editor.getEditor().renderer.setOptions({
            showFoldWidgets: false,
        });
    }
}
