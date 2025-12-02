import { Component, Input, OnChanges, SimpleChanges, ViewChild } from '@angular/core';
import { BuildAction, ScriptAction } from 'app/programming/shared/entities/build.action';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
// TODO: import { AeolusService } from 'app/programming/shared/services/aeolus.service';
import { ProgrammingExerciseBuildConfigurationComponent } from 'app/programming/manage/update/update-components/custom-build-plans/programming-exercise-build-configuration/programming-exercise-build-configuration.component';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { FormsModule } from '@angular/forms';
// TODO: import { TranslateDirective } from 'app/shared/language/translate.directive';
// TODO: import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
// TODO: import import { NgClass } from '@angular/common';
// TODO: import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { getDefaultContainerConfig } from 'app/programming/shared/entities/programming-exercise-build.config';

@Component({
    selector: 'jhi-programming-exercise-custom-aeolus-build-plan',
    templateUrl: './programming-exercise-custom-aeolus-build-plan.component.html',
    styleUrls: ['../../../../shared/programming-exercise-form.scss'],
    // imports: [FormsModule, TranslateDirective, HelpIconComponent, ProgrammingExerciseBuildConfigurationComponent, NgClass, MonacoEditorComponent, ArtemisTranslatePipe],
    imports: [FormsModule],
})
export class ProgrammingExerciseCustomAeolusBuildPlanComponent implements OnChanges {
    // private aeolusService = inject(AeolusService);

    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;

    @ViewChild(ProgrammingExerciseBuildConfigurationComponent) programmingExerciseDockerImageComponent?: ProgrammingExerciseBuildConfigurationComponent;

    programmingLanguage?: ProgrammingLanguage;
    projectType?: ProjectType;
    staticCodeAnalysisEnabled?: boolean;
    sequentialTestRuns?: boolean;

    code: string = '#!/bin/bash\n\n# Add your custom build plan action here';
    active?: BuildAction = undefined;
    isScriptAction = false;

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
            this.programmingExercise.buildConfig?.sequentialTestRuns !== this.sequentialTestRuns
        );
    }

    /**
     * In case the programming language or project type changes, we need to reset the template and the build plan
     * @private
     */
    resetCustomBuildPlan() {
        // TODO: this.programmingExercise.buildConfig!.windfile = undefined;
        getDefaultContainerConfig(this.programmingExercise.buildConfig!).buildPlanConfiguration = undefined;
    }

    /**
     * Loads the predefined template for the selected programming language and project type
     * if there is one available.
     * @param isImportFromFile whether the exercise is imported from a file
     * @private
     */
    loadAeolusTemplate(isImportFromFile: boolean = false) {
        if (this.programmingExercise?.id || isImportFromFile) {
            /* TODO if (!this.programmingExerciseCreationConfig.buildPlanLoaded && !this.programmingExercise.buildConfig?.windfile) {
                if (getDefaultContainerConfig(this.programmingExercise.buildConfig).buildPlanConfiguration) {
                    this.programmingExercise.buildConfig!.windfile = this.aeolusService.parseWindFile(
                        getDefaultContainerConfig(this.programmingExercise.buildConfig).buildPlanConfiguration!,
                    );
                }
                this.programmingExerciseCreationConfig.buildPlanLoaded = true;
            }*/
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
        /* TODO if (!isImportFromFile || !this.programmingExercise.buildConfig?.windfile) {
            this.aeolusService.getAeolusTemplateFile(this.programmingLanguage, this.projectType, this.staticCodeAnalysisEnabled, this.sequentialTestRuns).subscribe({
                next: (file) => {
                    this.programmingExercise.buildConfig!.windfile = this.aeolusService.parseWindFile(file);
                },
                error: () => {
                    this.programmingExercise.buildConfig!.windfile = undefined;
                },
            });
        }*/
        this.programmingExerciseCreationConfig.buildPlanLoaded = true;
    }

    get editor(): MonacoEditorComponent | undefined {
        return this._editor;
    }

    faQuestionCircle = faQuestionCircle;

    protected getActionScript(action: string): string {
        /* TODO const foundAction: BuildAction | undefined = this.programmingExercise.buildConfig?.windfile?.actions.find((a) => a.name === action);
        if (foundAction && foundAction instanceof ScriptAction) {
            return (foundAction as ScriptAction).script;
        } */
        return '';
    }

    changeActiveAction(action: string): void {
        /* TODO: if (!this.programmingExercise.buildConfig?.windfile) {
            return;
        }*/

        this.code = this.getActionScript(action);
        // TODO: this.active = this.programmingExercise.buildConfig?.windfile.actions.find((a) => a.name === action);
        this.isScriptAction = this.active instanceof ScriptAction;
        if (this.isScriptAction && this.editor) {
            this.editor.setText(this.code);
        }
    }

    deleteAction(action: string): void {
        /* TODO: if (this.programmingExercise.buildConfig?.windfile) {
            this.programmingExercise.buildConfig!.windfile.actions = this.programmingExercise.buildConfig?.windfile.actions.filter((a) => a.name !== action);
            if (this.active?.name === action) {
                this.active = undefined;
                this.code = '';
            }
        }*/
    }

    addAction(action: string): void {
        /* TODO: if (this.programmingExercise.buildConfig?.windfile) {
            const newAction = new ScriptAction();
            newAction.script = '#!/bin/bash\n\n# Add your custom build plan action here\n\nexit 0';
            newAction.name = action;
            newAction.runAlways = false;
            this.programmingExercise.buildConfig?.windfile.actions.push(newAction);
            this.changeActiveAction(action);
        }*/
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

    codeChanged(event: { text: string; fileName: string }): void {
        if (this.active instanceof ScriptAction) {
            (this.active as ScriptAction).script = event.text;
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
        /* TODO: if (!this.programmingExercise.buildConfig?.windfile || !this.programmingExercise.buildConfig?.windfile.metadata.docker) {
            return;
        }
        this.programmingExercise.buildConfig!.windfile.metadata.docker.image = dockerImage.trim();*/
    }
}
