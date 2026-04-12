import { Component, Input, OnChanges, OnInit, SimpleChanges, ViewChild, inject } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
import { BuildPhasesTemplateService } from 'app/programming/shared/services/build-phases-template.service';
import { ProgrammingExerciseBuildConfigurationComponent } from 'app/programming/manage/update/update-components/custom-build-plans/programming-exercise-build-configuration/programming-exercise-build-configuration.component';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { BuildPhasesEditorComponent } from 'app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/build-phases-editor.component';
import { BUILD_PHASE_NAME_PATTERN, BUILD_PHASE_RESERVED_NAMES, BuildPhase, BuildPlanPhases, parseBuildPlanPhases } from 'app/programming/shared/entities/build-plan-phases.model';
import { LegacyBuildPlanAdapterService } from 'app/programming/shared/services/legacy-build-plan-adapter.service';

@Component({
    selector: 'jhi-programming-exercise-custom-build-plan',
    templateUrl: './programming-exercise-custom-build-plan.component.html',
    styleUrls: ['../../../../shared/programming-exercise-form.scss'],
    imports: [FormsModule, TranslateDirective, HelpIconComponent, ProgrammingExerciseBuildConfigurationComponent, BuildPhasesEditorComponent],
})
export class ProgrammingExerciseCustomBuildPlanComponent implements OnChanges, OnInit {
    private buildPhasesTemplateService = inject(BuildPhasesTemplateService);
    private legacyBuildPlanAdapterService = inject(LegacyBuildPlanAdapterService);

    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;

    @ViewChild(ProgrammingExerciseBuildConfigurationComponent) programmingExerciseDockerImageComponent?: ProgrammingExerciseBuildConfigurationComponent;

    programmingLanguage?: ProgrammingLanguage;
    projectType?: ProjectType;
    staticCodeAnalysisEnabled?: boolean;
    sequentialTestRuns?: boolean;
    isImportFromFile = false;

    buildPlanPhases: BuildPlanPhases = {
        phases: [
            {
                name: '',
                script: '# enter the script of this phase',
                condition: 'ALWAYS',
                forceRun: false,
                resultPaths: [],
            },
        ],
    } as BuildPlanPhases;

    ngOnInit() {
        const buildConfig = this.programmingExercise.buildConfig;
        const configJson = buildConfig?.buildPlanConfiguration;
        if (configJson) {
            try {
                const parsed = parseBuildPlanPhases(configJson);
                if (parsed?.phases?.length) {
                    this.buildPlanPhases = parsed as BuildPlanPhases;
                    return;
                }
            } catch {
                // handled by legacy fallback below
            }
        }

        const legacyBuildScript = buildConfig?.buildScript;
        if (!legacyBuildScript?.trim() || !this.programmingExercise.programmingLanguage) {
            this.resetCustomBuildPlan();
            return;
        }

        // convert legacy format to the new phases
        this.legacyBuildPlanAdapterService
            .createBuildPhasesFromLegacyBuildScript(
                legacyBuildScript,
                configJson,
                this.programmingExercise.programmingLanguage,
                this.programmingExercise.projectType,
                this.programmingExercise.staticCodeAnalysisEnabled,
                this.programmingExercise.buildConfig?.sequentialTestRuns,
            )
            .subscribe({
                next: (buildPlanPhases) => {
                    this.buildPlanPhases = buildPlanPhases;
                },
            });
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.programmingExerciseCreationConfig || changes.programmingExercise) {
            if (this.shouldReloadTemplate()) {
                const isImportFromFile = changes.programmingExerciseCreationConfig?.currentValue?.isImportFromFile ?? false;
                this.loadBuildPhasesTemplate(isImportFromFile);
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
        this.programmingExercise.buildConfig!.buildPlanConfiguration = undefined;
        this.programmingExercise.buildConfig!.buildScript = undefined;
    }

    /**
     * Loads the predefined template for the selected programming language and project type
     * if there is one available.
     * @param isImportFromFile whether the exercise is imported from a file
     * @private
     */
    loadBuildPhasesTemplate(isImportFromFile: boolean = false) {
        if (!this.programmingExercise.programmingLanguage) {
            return;
        }
        this.programmingLanguage = this.programmingExercise.programmingLanguage;
        this.projectType = this.programmingExercise.projectType;
        this.staticCodeAnalysisEnabled = this.programmingExercise.staticCodeAnalysisEnabled;
        this.sequentialTestRuns = this.programmingExercise.buildConfig?.sequentialTestRuns;
        this.isImportFromFile = isImportFromFile;
        if (!isImportFromFile || !this.programmingExercise.buildConfig?.buildPlanConfiguration) {
            this.buildPhasesTemplateService.getTemplate(this.programmingLanguage, this.projectType, this.staticCodeAnalysisEnabled, this.sequentialTestRuns).subscribe({
                next: (buildPlanPhases) => {
                    if (!buildPlanPhases?.phases?.length) {
                        return;
                    }
                    this.buildPlanPhases = buildPlanPhases;
                },
                error: () => {
                    this.resetCustomBuildPlan();
                },
            });
        }
        this.programmingExerciseCreationConfig.buildPlanLoaded = true;
        if (!this.programmingExercise.buildConfig?.buildPlanConfiguration) {
            this.resetCustomBuildPlan();
        }
        if (!this.programmingExercise.buildConfig?.timeoutSeconds) {
            this.programmingExercise.buildConfig!.timeoutSeconds = 0;
        }
    }

    /**
     * Called when the build phases editor emits a change.
     */
    onPhasesChange(phases: BuildPhase[]) {
        this.buildPlanPhases = { ...this.buildPlanPhases, phases };
    }

    /**
     * Stores the selected Docker image alongside the current phase configuration.
     *
     * @param dockerImage the selected Docker image
     */
    setDockerImage(dockerImage: string) {
        this.buildPlanPhases = { ...this.buildPlanPhases, dockerImage: dockerImage.trim() };
    }

    /**
     * Returns the build plan phases as a JSON string including the docker image.
     * Used by the parent component to serialize phases into buildPlanConfiguration.
     * @returns JSON string of BuildPlanPhases with dockerImage, or undefined if no phases available
     */
    getBuildPlanPhasesJSON(): string | undefined {
        if (!this.buildPlanPhases?.phases?.length || !this.arePhaseNamesValid(this.buildPlanPhases.phases)) {
            return undefined;
        }
        return JSON.stringify(this.buildPlanPhases);
    }

    arePhaseNamesValid(phases: BuildPhase[]): boolean {
        const normalizedNames = phases.map((phase) => phase.name.toLowerCase());
        const namesAreUnique = new Set(normalizedNames).size === normalizedNames.length;
        const namesArePatternValid = phases.every((phase) => BUILD_PHASE_NAME_PATTERN.test(phase.name));
        const namesAreNotReserved = phases.every((phase) => !BUILD_PHASE_RESERVED_NAMES.has(phase.name.toLowerCase()));
        return namesAreUnique && namesArePatternValid && namesAreNotReserved;
    }

    setTimeout(timeout: number) {
        this.programmingExercise.buildConfig!.timeoutSeconds = timeout;
    }
}
