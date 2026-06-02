import { Component, OnInit, effect, inject, input, untracked, viewChild } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
import { BuildPhasesTemplateService } from 'app/programming/shared/services/build-phases-template.service';
import { ProgrammingExerciseBuildConfigurationComponent } from 'app/programming/manage/update/update-components/custom-build-plans/programming-exercise-build-configuration/programming-exercise-build-configuration.component';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { BuildPhasesEditorComponent } from 'app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/build-phases-editor.component';
import { BUILD_PHASE_NAME_PATTERN, BUILD_PHASE_RESERVED_NAMES, BuildPhase, BuildPlanPhases, parseBuildPlanPhases } from 'app/programming/shared/entities/build-plan-phases.model';
import { LegacyBuildPlanConverterService } from 'app/programming/shared/services/legacy-build-plan-converter.service';

@Component({
    selector: 'jhi-programming-exercise-custom-build-plan',
    templateUrl: './programming-exercise-custom-build-plan.component.html',
    styleUrls: ['../../../../shared/programming-exercise-form.scss'],
    imports: [FormsModule, TranslateDirective, HelpIconComponent, ProgrammingExerciseBuildConfigurationComponent, BuildPhasesEditorComponent],
})
export class ProgrammingExerciseCustomBuildPlanComponent implements OnInit {
    private buildPhasesTemplateService = inject(BuildPhasesTemplateService);
    private legacyBuildPlanConverterService = inject(LegacyBuildPlanConverterService);

    readonly programmingExercise = input.required<ProgrammingExercise>();
    readonly programmingExerciseCreationConfig = input.required<ProgrammingExerciseCreationConfig>();

    readonly programmingExerciseDockerImageComponent = viewChild(ProgrammingExerciseBuildConfigurationComponent);

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

    // Snapshot of the previously seen input references, used to reproduce the reference-change semantics
    // of the former ngOnChanges (which only fired when one of the two inputs changed by reference).
    private previousInputs: { programmingExercise?: ProgrammingExercise; programmingExerciseCreationConfig?: ProgrammingExerciseCreationConfig } = {};

    constructor() {
        // Replaces the former ngOnChanges: react when either input reference changes and, if a reload is
        // warranted, load the matching build-phases template. isImportFromFile is only derived from the
        // creation config when that input itself changed (matching the old changes.programmingExerciseCreationConfig
        // ?.currentValue?.isImportFromFile ?? false logic; a programmingExercise-only change yields false).
        effect(() => {
            const currentProgrammingExercise = this.programmingExercise();
            const currentCreationConfig = this.programmingExerciseCreationConfig();

            const programmingExerciseChanged = currentProgrammingExercise !== this.previousInputs.programmingExercise;
            const creationConfigChanged = currentCreationConfig !== this.previousInputs.programmingExerciseCreationConfig;
            this.previousInputs = { programmingExercise: currentProgrammingExercise, programmingExerciseCreationConfig: currentCreationConfig };

            if (!programmingExerciseChanged && !creationConfigChanged) {
                return;
            }

            untracked(() => {
                if (this.shouldReloadTemplate()) {
                    const isImportFromFile = creationConfigChanged ? (currentCreationConfig?.isImportFromFile ?? false) : false;
                    this.loadBuildPhasesTemplate(isImportFromFile);
                }
            });
        });
    }

    ngOnInit() {
        const buildConfig = this.programmingExercise().buildConfig;
        const configJson = buildConfig?.buildPlanConfiguration;
        if (configJson) {
            const parsed = parseBuildPlanPhases(configJson);
            if (parsed?.phases?.length) {
                this.buildPlanPhases = parsed as BuildPlanPhases;
                return;
            }
        }

        const legacyBuildScript = buildConfig?.buildScript;
        if (!legacyBuildScript?.trim() || !this.programmingExercise().programmingLanguage) {
            this.resetCustomBuildPlan();
            return;
        }

        // convert legacy format to the new phases
        const convertedBuildPlanPhases = this.legacyBuildPlanConverterService.convertLegacyBuildPlanConfiguration(legacyBuildScript, configJson);
        if (convertedBuildPlanPhases) {
            this.buildPlanPhases = convertedBuildPlanPhases;
            return;
        }

        this.resetCustomBuildPlan();
    }

    shouldReloadTemplate(): boolean {
        const programmingExercise = this.programmingExercise();
        return (
            !programmingExercise.id &&
            (programmingExercise.programmingLanguage !== this.programmingLanguage ||
                programmingExercise.projectType !== this.projectType ||
                programmingExercise.staticCodeAnalysisEnabled !== this.staticCodeAnalysisEnabled ||
                programmingExercise.buildConfig!.sequentialTestRuns !== this.sequentialTestRuns)
        );
    }

    /**
     * In case the programming language or project type changes, we need to reset the template and the build plan
     * @private
     */
    resetCustomBuildPlan() {
        this.programmingExercise().buildConfig!.buildPlanConfiguration = undefined;
        this.programmingExercise().buildConfig!.buildScript = undefined;
    }

    /**
     * Loads the predefined template for the selected programming language and project type
     * if there is one available.
     * @param isImportFromFile whether the exercise is imported from a file
     * @private
     */
    loadBuildPhasesTemplate(isImportFromFile: boolean = false) {
        const programmingExercise = this.programmingExercise();
        if (!programmingExercise.programmingLanguage) {
            return;
        }
        this.programmingLanguage = programmingExercise.programmingLanguage;
        this.projectType = programmingExercise.projectType;
        this.staticCodeAnalysisEnabled = programmingExercise.staticCodeAnalysisEnabled;
        this.sequentialTestRuns = programmingExercise.buildConfig?.sequentialTestRuns;
        this.isImportFromFile = isImportFromFile;
        if (!isImportFromFile || !programmingExercise.buildConfig?.buildPlanConfiguration) {
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
        this.programmingExerciseCreationConfig().buildPlanLoaded = true;
        if (!programmingExercise.buildConfig?.buildPlanConfiguration) {
            this.resetCustomBuildPlan();
        }
        if (!programmingExercise.buildConfig?.timeoutSeconds) {
            programmingExercise.buildConfig!.timeoutSeconds = 0;
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
        this.programmingExercise().buildConfig!.timeoutSeconds = timeout;
    }
}
