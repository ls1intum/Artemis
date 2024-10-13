import { Component, Input, OnChanges, SimpleChanges, ViewChild } from '@angular/core';
import { BuildAction, ScriptAction } from 'app/entities/programming/build.action';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming/programming-exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { AeolusService } from 'app/exercises/programming/shared/service/aeolus.service';
import { ProgrammingExerciseBuildConfigurationComponent } from 'app/exercises/programming/manage/update/update-components/custom-build-plans/programming-exercise-build-configuration/programming-exercise-build-configuration.component';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';

@Component({
    selector: 'jhi-programming-exercise-custom-aeolus-build-plan',
    templateUrl: './programming-exercise-custom-aeolus-build-plan.component.html',
    styleUrls: ['../../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseCustomAeolusBuildPlanComponent implements OnChanges {
    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;

    @ViewChild(ProgrammingExerciseBuildConfigurationComponent) programmingExerciseDockerImageComponent?: ProgrammingExerciseBuildConfigurationComponent;

    programmingLanguage?: ProgrammingLanguage;
    projectType?: ProjectType;
    staticCodeAnalysisEnabled?: boolean;
    sequentialTestRuns?: boolean;
    testwiseCoverageEnabled?: boolean;

    constructor(private aeolusService: AeolusService) {}

    code: string = '#!/bin/bash\n\n# Add your custom build plan action here';
    active?: BuildAction = undefined;
    isScriptAction: boolean = false;

    private _editor?: MonacoEditorComponent;

    @ViewChild('editor', { static: false }) set editor(value: MonacoEditorComponent) {
        this._editor = value;
        if (this._editor) {
            this.setupEditor();
            this._editor.setText(this.code);
        }
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.programmingExerciseCreationConfig || changes.programmingExercise) {
            if (this.shouldReloadTemplate()) {
                const isImportFromFile = changes.programmingExerciseCreationConfig?.currentValue?.isImportFromFile ?? false;
                this.loadAeolusTemplate(isImportFromFile);
            }
        }
    }

    shouldReloadTemplate(): boolean {
        return (
            this.programmingExercise.programmingLanguage !== this.programmingLanguage ||
            this.programmingExercise.projectType !== this.projectType ||
            this.programmingExercise.staticCodeAnalysisEnabled !== this.staticCodeAnalysisEnabled ||
            this.programmingExercise.buildConfig?.sequentialTestRuns !== this.sequentialTestRuns ||
            this.programmingExercise.buildConfig?.testwiseCoverageEnabled !== this.testwiseCoverageEnabled
        );
    }

    /**
     * In case the programming language or project type changes, we need to reset the template and the build plan
     * @private
     */
    resetCustomBuildPlan() {
        this.programmingExercise.buildConfig!.windfile = undefined;
        this.programmingExercise.buildConfig!.buildPlanConfiguration = undefined;
    }

    /**
     * Loads the predefined template for the selected programming language and project type
     * if there is one available.
     * @param isImportFromFile whether the exercise is imported from a file
     * @private
     */
    loadAeolusTemplate(isImportFromFile: boolean = false) {
        if (this.programmingExercise?.id || isImportFromFile) {
            if (!this.programmingExerciseCreationConfig.buildPlanLoaded && !this.programmingExercise.buildConfig?.windfile) {
                if (this.programmingExercise.buildConfig?.buildPlanConfiguration) {
                    this.programmingExercise.buildConfig!.windfile = this.aeolusService.parseWindFile(this.programmingExercise.buildConfig?.buildPlanConfiguration);
                }
                this.programmingExerciseCreationConfig.buildPlanLoaded = true;
            }
            return;
        }
        this.resetCustomBuildPlan();
        if (!this.programmingExercise.programmingLanguage) {
            return;
        }
        this.programmingLanguage = this.programmingExercise.programmingLanguage;
        this.projectType = this.programmingExercise.projectType;
        this.staticCodeAnalysisEnabled = this.programmingExercise.staticCodeAnalysisEnabled;
        this.sequentialTestRuns = this.programmingExercise.buildConfig?.sequentialTestRuns;
        this.testwiseCoverageEnabled = this.programmingExercise.buildConfig?.testwiseCoverageEnabled;
        if (!isImportFromFile || !this.programmingExercise.buildConfig?.windfile) {
            this.aeolusService
                .getAeolusTemplateFile(this.programmingLanguage, this.projectType, this.staticCodeAnalysisEnabled, this.sequentialTestRuns, this.testwiseCoverageEnabled)
                .subscribe({
                    next: (file) => {
                        this.programmingExercise.buildConfig!.windfile = this.aeolusService.parseWindFile(file);
                    },
                    error: () => {
                        this.programmingExercise.buildConfig!.windfile = undefined;
                    },
                });
        }
        this.programmingExerciseCreationConfig.buildPlanLoaded = true;
    }

    get editor(): MonacoEditorComponent | undefined {
        return this._editor;
    }

    faQuestionCircle = faQuestionCircle;

    protected getActionScript(action: string): string {
        const foundAction: BuildAction | undefined = this.programmingExercise.buildConfig?.windfile?.actions.find((a) => a.name === action);
        if (foundAction && foundAction instanceof ScriptAction) {
            return (foundAction as ScriptAction).script;
        }
        return '';
    }

    changeActiveAction(action: string): void {
        if (!this.programmingExercise.buildConfig?.windfile) {
            return;
        }

        this.code = this.getActionScript(action);
        this.active = this.programmingExercise.buildConfig?.windfile.actions.find((a) => a.name === action);
        this.isScriptAction = this.active instanceof ScriptAction;
        if (this.isScriptAction && this.editor) {
            this.editor.setText(this.code);
        }
    }

    deleteAction(action: string): void {
        if (this.programmingExercise.buildConfig?.windfile) {
            this.programmingExercise.buildConfig!.windfile.actions = this.programmingExercise.buildConfig?.windfile.actions.filter((a) => a.name !== action);
            if (this.active?.name === action) {
                this.active = undefined;
                this.code = '';
            }
        }
    }

    addAction(action: string): void {
        if (this.programmingExercise.buildConfig?.windfile) {
            const newAction = new ScriptAction();
            newAction.script = '#!/bin/bash\n\n# Add your custom build plan action here\n\nexit 0';
            newAction.name = action;
            newAction.runAlways = false;
            this.programmingExercise.buildConfig?.windfile.actions.push(newAction);
            this.changeActiveAction(action);
        }
    }

    addParameter(): void {
        if (this.active) {
            if (!this.active.parameters) {
                this.active.parameters = new Map<string, string | boolean | number>();
            }
            this.active.parameters.set('newParameter' + this.active.parameters.size, 'newValue');
        }
    }

    deleteParameter(key: string): void {
        if (this.active && this.active.parameters) {
            this.active.parameters.delete(key);
        }
    }

    codeChanged(code: string): void {
        if (this.active instanceof ScriptAction) {
            (this.active as ScriptAction).script = code;
        }
    }

    getParameterKeys(): string[] {
        if (this.active && this.active.parameters) {
            return Array.from(this.active.parameters.keys());
        }
        return [];
    }

    getParameter(key: string): string | number | boolean {
        return this.active?.parameters?.get(key) ?? '';
    }

    /**
     * Sets up a monaco editor for the template or solution file.
     */
    setupEditor(): void {
        if (!this._editor) {
            return;
        }
        this._editor.changeModel('build-script.sh', '');
    }

    setDockerImage(dockerImage: string) {
        if (!this.programmingExercise.buildConfig?.windfile || !this.programmingExercise.buildConfig?.windfile.metadata.docker) {
            return;
        }
        this.programmingExercise.buildConfig!.windfile.metadata.docker.image = dockerImage.trim();
    }
}
