import { Component, Input, OnChanges, SimpleChanges, ViewChild, effect, inject, signal, viewChild } from '@angular/core';
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
import { BuildPhasesEditor } from 'app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/build-phases-editor';
import { BuildPhase, BuildPlanPhases } from 'app/programming/shared/entities/build-plan-phases.model';
import { ScriptAction } from 'app/programming/shared/entities/build.action';
import { WindFile } from 'app/programming/shared/entities/wind.file';
import { isEqual } from 'lodash-es';

@Component({
    selector: 'jhi-programming-exercise-custom-build-plan',
    templateUrl: './programming-exercise-custom-build-plan.component.html',
    styleUrls: ['../../../../shared/programming-exercise-form.scss'],
    imports: [FormsModule, TranslateDirective, HelpIconComponent, ProgrammingExerciseBuildConfigurationComponent, BuildPhasesEditor],
})
export class ProgrammingExerciseCustomBuildPlanComponent implements OnChanges {
    private aeolusService = inject(AeolusService);

    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;

    @ViewChild(ProgrammingExerciseBuildConfigurationComponent) programmingExerciseDockerImageComponent?: ProgrammingExerciseBuildConfigurationComponent;

    editorHeight = signal(100);

    programmingLanguage?: ProgrammingLanguage;
    projectType?: ProjectType;
    staticCodeAnalysisEnabled?: boolean;
    sequentialTestRuns?: boolean;
    isImportFromFile = false;

    code: string = '#!/bin/bash\n\n# Add your custom build plan action here';
    private _editor?: MonacoEditorComponent;

    readonly phaseEditor = viewChild<BuildPhasesEditor>('phase_editor');

    constructor() {
        effect(() => {
            const editorInstance = this.phaseEditor();
            if (!editorInstance) {
                return;
            }

            const windfile = this.programmingExercise.buildConfig?.windfile;

            if (windfile?.actions?.length) {
                // use windfile actions to populate phases (with script + result paths)
                editorInstance.initialize(this.buildPlanFromWindfile(windfile));
                return;
            }

            // Fallback: use raw build script in a single phase
            if (this.programmingExercise.id || this.isImportFromFile) {
                this.code = this.programmingExercise.buildConfig?.buildScript || '';
            }
            const planFromSingleScript: BuildPlanPhases = {
                phases: [
                    {
                        name: 'script',
                        script: this.code,
                        condition: 'ALWAYS',
                        resultPaths: [],
                    },
                ],
            };
            editorInstance.initialize(planFromSingleScript);
        });
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
        this.programmingExercise.buildConfig!.windfile = undefined;
        this.programmingExercise.buildConfig!.buildPlanConfiguration = undefined;
        this.programmingExercise.buildConfig!.buildScript = undefined;
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
        if (!isImportFromFile || !this.programmingExercise.buildConfig?.windfile) {
            this.aeolusService.getAeolusTemplateFile(this.programmingLanguage, this.projectType, this.staticCodeAnalysisEnabled, this.sequentialTestRuns).subscribe({
                next: (file) => {
                    this.programmingExercise.buildConfig!.windfile = this.aeolusService.parseWindFile(file);

                    const editorInstance = this.phaseEditor();
                    const windfile = this.programmingExercise.buildConfig!.windfile;
                    if (!editorInstance || !windfile?.actions?.length) {
                        return;
                    }
                    const newPhases = this.buildPlanFromWindfile(windfile);
                    const newPhasesSame = isEqual(newPhases, editorInstance.getBuildPlanPhases());
                    if (newPhasesSame) {
                        return;
                    }
                    // re-initialize the phase editor with windfile data
                    // (e.g. update phase editor when sequential test is toggled)
                    editorInstance.initialize(newPhases);
                },
                error: () => {
                    this.programmingExercise.buildConfig!.windfile = undefined;
                },
            });
        }
        this.programmingExerciseCreationConfig.buildPlanLoaded = true;
        if (!this.programmingExercise.buildConfig?.windfile) {
            this.resetCustomBuildPlan();
        }
        if (!isImportFromFile || !this.programmingExercise.buildConfig?.buildScript) {
            this.aeolusService.getAeolusTemplateScript(this.programmingLanguage, this.projectType, this.staticCodeAnalysisEnabled, this.sequentialTestRuns).subscribe({
                next: (file: string) => {
                    file = this.replacePlaceholders(file);
                    this.codeChanged(file);
                    this.editor?.setText(file);
                },
                error: () => {
                    this.programmingExercise.buildConfig!.buildScript = undefined;
                },
            });
        }
        if (!this.programmingExercise.buildConfig?.buildScript) {
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

    codeChanged(codeOrEvent: string | { text: string; fileName: string }): void {
        const code = typeof codeOrEvent === 'string' ? codeOrEvent : codeOrEvent.text;
        this.code = code;
        this.editor?.setText(code);
        this.programmingExercise.buildConfig!.buildScript = code;
    }

    setDockerImage(dockerImage: string) {
        if (!this.programmingExercise.buildConfig?.windfile || !this.programmingExercise.buildConfig?.windfile.metadata.docker) {
            return;
        }
        this.programmingExercise.buildConfig!.windfile.metadata.docker.image = dockerImage.trim();
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

    /**
     * Converts windfile actions into BuildPlanPhases format.
     * Only ScriptActions (actions with a script property) are included.
     * @param windfile The windfile containing actions to convert
     * @returns BuildPlanPhases with one phase per script action
     */
    private buildPlanFromWindfile(windfile: WindFile): BuildPlanPhases {
        const phases: BuildPhase[] = windfile.actions
            .filter((action): action is ScriptAction => 'script' in action && !!action.script)
            .map((action) => ({
                name: action.name || '',
                script: this.replacePlaceholders(action.script),
                condition: 'ALWAYS' as const,
                resultPaths: (action.results ?? []).map((r) => r.path).filter((p): p is string => !!p),
            }));
        return { phases };
    }
}
