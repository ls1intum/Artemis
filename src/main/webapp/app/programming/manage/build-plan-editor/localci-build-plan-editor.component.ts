import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { faPlayCircle } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';
import { TumUiTooltipDirective } from 'app/shared-ui/tum-ui/tooltip/tum-ui-tooltip.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { BuildPlanConfigurationService } from 'app/programming/manage/services/build-plan-configuration.service';
import { LegacyBuildPlanConverterService } from 'app/programming/shared/services/legacy-build-plan-converter.service';
import { BuildPhasesTemplateService } from 'app/programming/shared/services/build-phases-template.service';
import { BUILD_PHASE_NAME_PATTERN, BUILD_PHASE_RESERVED_NAMES, BuildPhase, parseBuildPlanPhases } from 'app/programming/shared/entities/build-plan-phases.model';
import { ProgrammingExerciseBuildConfigurationComponent } from 'app/programming/manage/build-plan-editor/programming-exercise-build-configuration/programming-exercise-build-configuration.component';
import { BuildPhasesEditorComponent } from 'app/programming/manage/build-plan-editor/build-phases-editor/build-phases-editor.component';

/**
 * Dedicated build plan editor page for LocalCI. It edits the structured build plan configuration (build phases, Docker
 * image, Docker flags, and timeout) of a programming exercise on its own page, and shows the live template and solution
 * build status so that an instructor can immediately see whether the new build plan works.
 */
@Component({
    selector: 'jhi-localci-build-plan-editor',
    templateUrl: './localci-build-plan-editor.component.html',
    imports: [
        TranslateDirective,
        ArtemisTranslatePipe,
        TumUiButtonComponent,
        TumUiTooltipDirective,
        HelpIconComponent,
        UpdatingResultComponent,
        ProgrammingExerciseBuildConfigurationComponent,
        BuildPhasesEditorComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LocalCIBuildPlanEditorComponent implements OnInit {
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private buildPlanConfigurationService = inject(BuildPlanConfigurationService);
    private legacyBuildPlanConverterService = inject(LegacyBuildPlanConverterService);
    private buildPhasesTemplateService = inject(BuildPhasesTemplateService);
    private alertService = inject(AlertService);
    private activatedRoute = inject(ActivatedRoute);
    private destroyRef = inject(DestroyRef);

    protected readonly farPlayCircle = faPlayCircle;

    readonly programmingExercise = signal<ProgrammingExercise | undefined>(undefined);
    readonly loadingResults = signal(true);
    readonly isSaving = signal(false);

    readonly phases = signal<BuildPhase[]>([]);
    readonly dockerImage = signal<string>('');
    readonly timeout = signal<number>(0);

    // set while the editor waits for the build phases template so the fetched language default is only applied when the
    // exercise itself configured no image, and never overwrites an image the instructor typed in the meantime
    private readonly awaitingTemplateDockerImage = signal(false);

    constructor() {
        // Seed the language default image once the template service resolves it, so an exercise stored without an image
        // (the windfile migration did this for every exercise that relied on the language default) opens with a usable
        // image instead of an empty field and a dead submit button.
        effect(() => {
            const templateDockerImage = this.buildPhasesTemplateService.buildPlan()?.dockerImage;
            untracked(() => {
                if (this.awaitingTemplateDockerImage() && templateDockerImage && this.dockerImage().trim().length === 0) {
                    this.dockerImage.set(templateDockerImage);
                    this.awaitingTemplateDockerImage.set(false);
                }
            });
        });
    }

    readonly isExamMode = computed(() => !!this.programmingExercise()?.exerciseGroup);

    private readonly buildConfigurationComponent = viewChild(ProgrammingExerciseBuildConfigurationComponent);

    readonly arePhaseNamesValid = computed(() => {
        const phases = this.phases();
        const normalizedNames = phases.map((phase) => phase.name.toLowerCase());
        const namesAreUnique = new Set(normalizedNames).size === normalizedNames.length;
        const namesArePatternValid = phases.every((phase) => BUILD_PHASE_NAME_PATTERN.test(phase.name));
        const namesAreNotReserved = normalizedNames.every((name) => !BUILD_PHASE_RESERVED_NAMES.has(name));
        return namesAreUnique && namesArePatternValid && namesAreNotReserved;
    });

    readonly isTimeoutValid = computed(() => {
        const buildConfigurationComponent = this.buildConfigurationComponent();
        // before the build configuration component has initialized its bounds from the profile info, do not block saving
        const min = buildConfigurationComponent?.timeoutMinValue();
        const max = buildConfigurationComponent?.timeoutMaxValue();
        const timeout = this.timeout();
        return (min === undefined || timeout >= min) && (max === undefined || timeout <= max);
    });

    // an empty image is allowed: submit() sends no image and the server falls back to the exercise's language default
    readonly canSubmit = computed(() => this.phases().length > 0 && this.arePhaseNamesValid() && this.isTimeoutValid());

    ngOnInit(): void {
        this.activatedRoute.data.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(({ exercise }) => {
            this.initEditingState(exercise);
            this.seedDefaultDockerImageIfMissing(exercise);
            this.programmingExercise.set(exercise);
            this.loadParticipationsWithResults(exercise);
        });
    }

    /**
     * Requests the language default Docker image from the build phases template service when the exercise configured
     * none. The resolved image is applied by the constructor effect. Does nothing when the image is already set or the
     * exercise has no programming language.
     */
    private seedDefaultDockerImageIfMissing(exercise: ProgrammingExercise): void {
        const programmingLanguage = exercise.programmingLanguage;
        if (!programmingLanguage || this.dockerImage().trim().length > 0) {
            return;
        }
        this.awaitingTemplateDockerImage.set(true);
        // the template service holds a shared signal, so reset it first: otherwise the effect would apply the image left
        // over from a previously opened exercise before this request resolves. The default plan carries no image.
        this.buildPhasesTemplateService.resetToDefault();
        this.buildPhasesTemplateService.fetchTemplate(!!exercise.exerciseGroup, programmingLanguage, exercise.projectType);
    }

    /**
     * Initializes the editable build plan state (phases, Docker image, timeout) from the exercise's build config.
     */
    private initEditingState(exercise: ProgrammingExercise): void {
        const buildConfig = exercise.buildConfig;
        this.timeout.set(buildConfig?.timeoutSeconds ?? 0);

        const configJson = buildConfig?.buildPlanConfiguration;
        const parsed = parseBuildPlanPhases(configJson);
        if (parsed?.phases?.length) {
            this.phases.set(parsed.phases);
            this.dockerImage.set(parsed.dockerImage ?? '');
            return;
        }

        // No structured phases yet: convert a legacy build script or older configuration format so an existing exercise
        // keeps its build plan instead of opening with an empty editor (which would overwrite the script on save).
        const converted = this.legacyBuildPlanConverterService.convertLegacyBuildPlanConfiguration(buildConfig?.buildScript, configJson);
        this.phases.set(converted?.phases ?? []);
        this.dockerImage.set(converted?.dockerImage ?? parsed?.dockerImage ?? '');
    }

    /**
     * Loads the template and solution participations with their latest results for the live build status, while keeping
     * the full build config of the resolved exercise (the participation query may not return it).
     */
    private loadParticipationsWithResults(resolvedExercise: ProgrammingExercise): void {
        this.programmingExerciseService
            .findWithTemplateAndSolutionParticipationAndLatestResults(resolvedExercise.id!)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (response) => {
                    const exercise = response.body!;
                    exercise.buildConfig = resolvedExercise.buildConfig;
                    this.programmingExercise.set(exercise);
                    this.loadingResults.set(false);
                },
                error: (error) => {
                    // the editor stays usable with the resolved exercise; only the live build status is unavailable
                    this.loadingResults.set(false);
                    onError(this.alertService, error);
                },
            });
    }

    /**
     * Persists the edited build plan configuration via the dedicated build-config endpoint.
     */
    submit(): void {
        const exercise = this.programmingExercise();
        if (!exercise?.id || !this.canSubmit()) {
            return;
        }
        this.isSaving.set(true);
        this.buildPlanConfigurationService
            .updateBuildPlanConfiguration(exercise.id, {
                // the image is validated trimmed, so it is also stored trimmed instead of keeping the whitespace an instructor pasted
                buildPlan: { phases: this.phases(), dockerImage: this.dockerImage().trim() || undefined },
                timeoutSeconds: this.timeout(),
                dockerFlags: exercise.buildConfig?.dockerFlags,
            })
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.isSaving.set(false);
                    this.alertService.success('artemisApp.programmingExercise.buildPlanConfiguration.saved');
                },
                error: (error) => {
                    this.isSaving.set(false);
                    onError(this.alertService, error);
                },
            });
    }
}
