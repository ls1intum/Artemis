import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { faPlayCircle } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonComponent, TumUiTooltipDirective } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { ComponentCanDeactivate } from 'app/foundation/guard/can-deactivate.model';
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
export class LocalCIBuildPlanEditorComponent implements OnInit, ComponentCanDeactivate {
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
    // the language default image shown as a placeholder while the field is empty, so the instructor sees what the build
    // falls back to without the value being seeded into the field and pinned on the next save
    readonly defaultDockerImage = signal<string>('');
    readonly timeout = signal<number>(0);

    readonly isExamMode = computed(() => !!this.programmingExercise()?.exerciseGroup);

    // a snapshot of the last persisted (or seeded) editor state; the editor is "dirty" when the current state differs, so
    // navigating away without saving prompts a confirmation instead of silently discarding the edits
    private readonly persistedSnapshot = signal<string>('');

    private readonly buildConfigurationComponent = viewChild(ProgrammingExerciseBuildConfigurationComponent);

    readonly arePhaseNamesValid = computed(() => {
        const phases = this.phases();
        const normalizedNames = phases.map((phase) => phase.name.toLowerCase());
        const namesAreUnique = new Set(normalizedNames).size === normalizedNames.length;
        const namesArePatternValid = phases.every((phase) => BUILD_PHASE_NAME_PATTERN.test(phase.name));
        const namesAreNotReserved = normalizedNames.every((name) => !BUILD_PHASE_RESERVED_NAMES.has(name));
        return namesAreUnique && namesArePatternValid && namesAreNotReserved;
    });

    // the timeout bounds come from the build configuration child once it has read the profile info; exposed so the
    // template can render the valid range in the out-of-bounds message
    readonly timeoutMinValue = computed(() => this.buildConfigurationComponent()?.timeoutMinValue());
    readonly timeoutMaxValue = computed(() => this.buildConfigurationComponent()?.timeoutMaxValue());

    readonly isTimeoutValid = computed(() => {
        const timeout = this.timeout();
        // 0 is the "use the global default timeout" sentinel (see BuildJobManagementService), so it is always valid and
        // must not be measured against the profile bounds
        if (timeout === 0) {
            return true;
        }
        // before the build configuration component has initialized its bounds from the profile info, do not block saving
        const min = this.timeoutMinValue();
        const max = this.timeoutMaxValue();
        return (min === undefined || timeout >= min) && (max === undefined || timeout <= max);
    });

    // a phase with a blank script is dropped from the stored configuration by the server's NON_EMPTY serialization and
    // corrupts the whole plan on reopen, so block saving until every phase carries a script
    readonly areScriptsValid = computed(() => this.phases().every((phase) => phase.script.trim().length > 0));

    // an empty image is allowed: submit() sends no image and the server falls back to the exercise's language default
    readonly canSubmit = computed(() => this.phases().length > 0 && this.arePhaseNamesValid() && this.areScriptsValid() && this.isTimeoutValid());

    ngOnInit(): void {
        this.activatedRoute.data.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(({ exercise }) => {
            this.initEditingState(exercise);
            this.programmingExercise.set(exercise);
            this.captureBaseline();
            this.seedDefaultsFromTemplate(exercise);
            this.loadParticipationsWithResults(exercise);
        });
    }

    /**
     * Fetches the language default template to fill in what the exercise left implicit, without pinning those defaults on
     * the next save:
     * <ul>
     * <li>when the exercise configured no image, the default is shown only as a placeholder ({@link defaultDockerImage}),
     * so the field stays empty and {@code submit} keeps sending no image (the exercise follows future default bumps);</li>
     * <li>when the exercise has no structured phases (a null configuration builds on the language default at build time),
     * the default phases are seeded into the editor so it opens with the real default plan instead of an empty list.</li>
     * </ul>
     * Does nothing when neither is needed or the exercise has no programming language. The response is tied to the
     * exercise it was requested for, so a slow response for a previously opened exercise never leaks into the one now on
     * screen.
     */
    private seedDefaultsFromTemplate(exercise: ProgrammingExercise): void {
        const programmingLanguage = exercise.programmingLanguage;
        const needsImagePlaceholder = this.dockerImage().trim().length === 0;
        const needsPhases = this.phases().length === 0;
        if (!programmingLanguage || (!needsImagePlaceholder && !needsPhases)) {
            return;
        }
        this.buildPhasesTemplateService
            .getTemplate(!!exercise.exerciseGroup, programmingLanguage, exercise.projectType)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (template) => {
                    if (this.programmingExercise()?.id !== exercise.id) {
                        return;
                    }
                    if (needsImagePlaceholder && template.dockerImage) {
                        this.defaultDockerImage.set(template.dockerImage);
                    }
                    if (needsPhases && template.phases?.length) {
                        this.phases.set(template.phases);
                        // seeded defaults are not user edits, so fold them into the baseline to avoid a false "unsaved changes" prompt
                        this.captureBaseline();
                    }
                },
                // the editor stays usable without the template; the instructor can still author the plan manually
                error: () => {},
            });
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
                    if (this.programmingExercise()?.id !== resolvedExercise.id) {
                        return;
                    }
                    const exercise = response.body!;
                    exercise.buildConfig = resolvedExercise.buildConfig;
                    this.programmingExercise.set(exercise);
                    this.loadingResults.set(false);
                },
                error: (error) => {
                    if (this.programmingExercise()?.id !== resolvedExercise.id) {
                        return;
                    }
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
        // guard against a second in-flight save: a double click would otherwise send two identical PUTs and trigger the
        // template and solution builds twice
        if (!exercise?.id || !this.canSubmit() || this.isSaving()) {
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
                    // the current state is now persisted, so it becomes the new baseline and leaving no longer prompts
                    this.captureBaseline();
                    this.alertService.success('artemisApp.programmingExercise.buildPlanConfiguration.saved');
                },
                error: (error) => {
                    this.isSaving.set(false);
                    onError(this.alertService, error);
                },
            });
    }

    /**
     * Serializes the editable state (phases, Docker image, timeout) so it can be compared against the persisted baseline.
     */
    private snapshot(): string {
        return JSON.stringify({ phases: this.phases(), dockerImage: this.dockerImage(), timeout: this.timeout() });
    }

    /**
     * Records the current editable state as the persisted baseline, marking the editor clean.
     */
    private captureBaseline(): void {
        this.persistedSnapshot.set(this.snapshot());
    }

    /**
     * Allows leaving the page without a prompt only when there are no unsaved edits (the current state still matches the
     * persisted baseline). The {@link PendingChangesGuard} shows the confirmation when this returns false.
     */
    canDeactivate(): boolean {
        return this.snapshot() === this.persistedSnapshot();
    }
}
