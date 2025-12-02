import { Component, Input, OnChanges, SimpleChanges, ViewChild, inject } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
import { AeolusService } from 'app/programming/shared/services/aeolus.service';
import { ProgrammingExerciseBuildConfigurationComponent } from 'app/programming/manage/update/update-components/custom-build-plans/programming-exercise-build-configuration/programming-exercise-build-configuration.component';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { ASSIGNMENT_REPO_NAME, TEST_REPO_NAME } from 'app/shared/constants/input.constants';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { getDefaultContainerConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { DockerContainerConfig } from 'app/programming/shared/entities/docker-container.config';

@Component({
    selector: 'jhi-programming-exercise-custom-build-plan',
    templateUrl: './programming-exercise-custom-build-plan.component.html',
    styleUrls: ['../../../../shared/programming-exercise-form.scss'],
    imports: [FormsModule, TranslateDirective, HelpIconComponent, ProgrammingExerciseBuildConfigurationComponent],
})
export class ProgrammingExerciseCustomBuildPlanComponent implements OnChanges {
    private aeolusService = inject(AeolusService);

    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;

    @ViewChild(ProgrammingExerciseBuildConfigurationComponent) programmingExerciseDockerImageComponent?: ProgrammingExerciseBuildConfigurationComponent;

    programmingLanguage?: ProgrammingLanguage;
    projectType?: ProjectType;
    staticCodeAnalysisEnabled?: boolean;
    sequentialTestRuns?: boolean;
    isImportFromFile = false;

    code: string = '#!/bin/bash\n\n# Add your custom build plan action here';
    private _editor?: MonacoEditorComponent;

    @ViewChild('editor', { static: false }) set editor(value: MonacoEditorComponent) {
        this._editor = value;
        if (this._editor) {
            this.setupEditor();
            if (this.programmingExercise.id || this.isImportFromFile) {
                this.code = getDefaultContainerConfig(this.programmingExercise.buildConfig).buildScript || '';
            }
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
            !this.programmingExercise.id &&
            (this.programmingExercise.programmingLanguage !== this.programmingLanguage ||
                this.programmingExercise.projectType !== this.projectType ||
                this.programmingExercise.staticCodeAnalysisEnabled !== this.staticCodeAnalysisEnabled ||
                this.programmingExercise.buildConfig!.sequentialTestRuns !== this.sequentialTestRuns)
        );
    }

    /**
     * In case the programming language or project type changes, we need to reset the template and the build plan
     * @private
     */
    resetCustomBuildPlan() {
        // TODO: this.programmingExercise.buildConfig!.windfile = undefined;
        getDefaultContainerConfig(this.programmingExercise.buildConfig!).buildPlanConfiguration = undefined;
        getDefaultContainerConfig(this.programmingExercise.buildConfig!).buildScript = undefined;
    }

    /**
     * Loads the predefined template for the selected programming language and project type
     * if there is one available.
     * @param isImportFromFile whether the exercise is imported from a file
     * @private
     */
    loadAeolusTemplate(isImportFromFile: boolean = false) {
        if (!this.programmingExercise.programmingLanguage) {
            return;
        }
        this.programmingLanguage = this.programmingExercise.programmingLanguage;
        this.projectType = this.programmingExercise.projectType;
        this.staticCodeAnalysisEnabled = this.programmingExercise.staticCodeAnalysisEnabled;
        this.sequentialTestRuns = this.programmingExercise.buildConfig?.sequentialTestRuns;
        this.isImportFromFile = isImportFromFile;
        /* TODO: if (!isImportFromFile || !this.programmingExercise.buildConfig?.windfile) {
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
        /* TODO: if (!this.programmingExercise.buildConfig?.windfile) {
            this.resetCustomBuildPlan();
        }*/
        if (!isImportFromFile || !getDefaultContainerConfig(this.programmingExercise.buildConfig).buildScript) {
            this.aeolusService.getAeolusTemplateScript(this.programmingLanguage, this.projectType, this.staticCodeAnalysisEnabled, this.sequentialTestRuns).subscribe({
                next: (file: string) => {
                    file = this.replacePlaceholders(file);
                    this.codeChanged(file);
                    this.editor?.setText(file);
                },
                error: () => {
                    getDefaultContainerConfig(this.programmingExercise.buildConfig!).buildScript = undefined;
                },
            });
        }
        if (!getDefaultContainerConfig(this.programmingExercise.buildConfig).buildScript) {
            this.resetCustomBuildPlan();
        }
        if (!this.programmingExercise.buildConfig?.timeoutSeconds) {
            this.programmingExercise.buildConfig!.timeoutSeconds = 0;
        }
    }

    get editor(): MonacoEditorComponent | undefined {
        return this._editor;
    }

    faQuestionCircle = faQuestionCircle;

    // TODO: This has to be moved aswell, later!
    codeChanged(codeOrEvent: string | { text: string; fileName: string }): void {
        const code = typeof codeOrEvent === 'string' ? codeOrEvent : codeOrEvent.text;
        this.code = code;
        this.editor?.setText(code);
        getDefaultContainerConfig(this.programmingExercise.buildConfig!).buildScript = code;
    }

    /**
     * Sets up the Monaco editor for the build plan script
     */
    setupEditor(): void {
        if (!this._editor) {
            return;
        }
        this._editor.changeModel('build-plan.sh', '');
    }

    onContainerConfigsChange(containerConfigs: { [key: string]: DockerContainerConfig }) {
        this.programmingExercise.buildConfig!.containerConfigs = containerConfigs;
        /* TODO: if (!this.programmingExercise.buildConfig?.windfile || !this.programmingExercise.buildConfig?.windfile.metadata.docker) {
            return;
        }*/
        // TODO: this.programmingExercise.buildConfig!.windfile.metadata.docker.image = dockerImage.trim();
    }

    setTimeout(timeout: number) {
        this.programmingExercise.buildConfig!.timeoutSeconds = timeout;
    }

    replacePlaceholders(buildScript: string): string {
        const assignmentRepoName = this.programmingExercise.buildConfig?.assignmentCheckoutPath || ASSIGNMENT_REPO_NAME;
        const testRepoName = this.programmingExercise.buildConfig?.testCheckoutPath || TEST_REPO_NAME;
        buildScript = buildScript.replaceAll('${studentParentWorkingDirectoryName}', assignmentRepoName);
        buildScript = buildScript.replaceAll('${testWorkingDirectory}', testRepoName);
        return buildScript;
    }
}
