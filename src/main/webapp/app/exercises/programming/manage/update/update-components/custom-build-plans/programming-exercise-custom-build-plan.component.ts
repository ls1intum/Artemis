import { Component, Input, OnChanges, SimpleChanges, ViewChild } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { AeolusService } from 'app/exercises/programming/shared/service/aeolus.service';
import { ProgrammingExerciseDockerImageComponent } from 'app/exercises/programming/manage/update/update-components/custom-build-plans/programming-exercise-docker-image/programming-exercise-docker-image.component';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';

@Component({
    selector: 'jhi-programming-exercise-custom-build-plan',
    templateUrl: './programming-exercise-custom-build-plan.component.html',
    styleUrls: ['../../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseCustomBuildPlanComponent implements OnChanges {
    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;

    @ViewChild(ProgrammingExerciseDockerImageComponent) programmingExerciseDockerImageComponent?: ProgrammingExerciseDockerImageComponent;

    programmingLanguage?: ProgrammingLanguage;
    projectType?: ProjectType;
    staticCodeAnalysisEnabled?: boolean;
    sequentialTestRuns?: boolean;
    testwiseCoverageEnabled?: boolean;
    isImportFromFile: boolean = false;

    constructor(private aeolusService: AeolusService) {}

    code: string = '#!/bin/bash\n\n# Add your custom build plan action here';
    private _editor?: MonacoEditorComponent;

    @ViewChild('editor', { static: false }) set editor(value: MonacoEditorComponent) {
        this._editor = value;
        if (this._editor) {
            this.setupEditor();
            if (this.programmingExercise.id || this.isImportFromFile) {
                this.code = this.programmingExercise.buildScript || '';
            }
            this._editor.setText(this.code);
        }
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.programmingExerciseCreationConfig || changes.programmingExercise) {
            if (this.shouldReloadTemplate()) {
                this.loadAeolusTemplate(changes.programmingExerciseCreationConfig.currentValue.isImportFromFile);
            }
        }
    }

    shouldReloadTemplate(): boolean {
        return (
            !this.programmingExercise.id &&
            (this.programmingExercise.programmingLanguage !== this.programmingLanguage ||
                this.programmingExercise.projectType !== this.projectType ||
                this.programmingExercise.staticCodeAnalysisEnabled !== this.staticCodeAnalysisEnabled ||
                this.programmingExercise.sequentialTestRuns !== this.sequentialTestRuns ||
                this.programmingExercise.testwiseCoverageEnabled !== this.testwiseCoverageEnabled)
        );
    }

    /**
     * In case the programming language or project type changes, we need to reset the template and the build plan
     * @private
     */
    resetCustomBuildPlan() {
        this.programmingExercise.windfile = undefined;
        this.programmingExercise.buildPlanConfiguration = undefined;
        this.programmingExercise.buildScript = undefined;
    }

    /**
     * Loads the predefined template for the selected programming language and project type
     * if there is one available.
     * @private
     */
    loadAeolusTemplate(isImportFromFile: boolean = false) {
        if (!this.programmingExercise.programmingLanguage) {
            return;
        }
        this.programmingLanguage = this.programmingExercise.programmingLanguage;
        this.projectType = this.programmingExercise.projectType;
        this.staticCodeAnalysisEnabled = this.programmingExercise.staticCodeAnalysisEnabled;
        this.sequentialTestRuns = this.programmingExercise.sequentialTestRuns;
        this.testwiseCoverageEnabled = this.programmingExercise.testwiseCoverageEnabled;
        this.isImportFromFile = isImportFromFile;
        if (!isImportFromFile || !this.programmingExercise.windfile) {
            this.aeolusService
                .getAeolusTemplateFile(this.programmingLanguage, this.projectType, this.staticCodeAnalysisEnabled, this.sequentialTestRuns, this.testwiseCoverageEnabled)
                .subscribe({
                    next: (file) => {
                        this.programmingExercise.windfile = this.aeolusService.parseWindFile(file);
                    },
                    error: () => {
                        this.programmingExercise.windfile = undefined;
                    },
                });
        }
        this.programmingExerciseCreationConfig.buildPlanLoaded = true;
        if (!this.programmingExercise.windfile) {
            this.resetCustomBuildPlan();
        }
        if (!isImportFromFile || !this.programmingExercise.buildScript) {
            this.aeolusService
                .getAeolusTemplateScript(this.programmingLanguage, this.projectType, this.staticCodeAnalysisEnabled, this.sequentialTestRuns, this.testwiseCoverageEnabled)
                .subscribe({
                    next: (file: string) => {
                        this.codeChanged(file);
                        this.editor?.setText(file);
                    },
                    error: () => {
                        this.programmingExercise.buildScript = undefined;
                    },
                });
        }
        if (!this.programmingExercise.buildScript) {
            this.resetCustomBuildPlan();
        }
    }

    get editor(): MonacoEditorComponent | undefined {
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
        this._editor.changeModel('build-plan.sh', '');
    }

    setDockerImage(dockerImage: string) {
        if (!this.programmingExercise.windfile || !this.programmingExercise.windfile.metadata.docker) {
            return;
        }
        this.programmingExercise.windfile.metadata.docker.image = dockerImage.trim();
    }
}
