import { ChangeDetectionStrategy, Component, DestroyRef, HostListener, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { faPlayCircle, faPlus } from '@fortawesome/free-solid-svg-icons';
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
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { BuildPhasesTemplateService } from 'app/programming/shared/services/build-phases-template.service';
import {
    BUILD_CONTAINER_NAME_PATTERN,
    BUILD_PHASE_NAME_PATTERN,
    BUILD_PHASE_RESERVED_NAMES,
    BuildContainer,
    DEFAULT_BUILD_CONTAINER_NAME,
    effectiveContainers,
    parseBuildPlanPhases,
} from 'app/programming/shared/entities/build-plan-phases.model';
import { BUILD_PLAN_CONFIGURATION_MAX_LENGTH } from 'app/programming/shared/entities/programming-exercise-build.config';
import { BuildContainerEditorComponent } from 'app/programming/manage/build-plan-editor/build-container-editor/build-container-editor.component';
import { ProgrammingExerciseBuildConfigurationComponent } from 'app/programming/manage/build-plan-editor/programming-exercise-build-configuration/programming-exercise-build-configuration.component';

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
        BuildContainerEditorComponent,
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
    private translateService = inject(TranslateService);
    private destroyRef = inject(DestroyRef);

    protected readonly farPlayCircle = faPlayCircle;

    protected readonly faPlus = faPlus;

    readonly programmingExercise = signal<ProgrammingExercise | undefined>(undefined);
    readonly loadingResults = signal(true);
    readonly isSaving = signal(false);

    readonly containers = signal<BuildContainer[]>([]);
    // the language default image, recorded so the container editor can offer it instead of pinning it into a field
    readonly defaultDockerImage = signal<string>('');
    readonly timeout = signal<number>(0);

    readonly isExamMode = computed(() => !!this.programmingExercise()?.exerciseGroup);

    // a snapshot of the last persisted (or seeded) editor state; the editor is "dirty" when the current state differs, so
    // navigating away without saving prompts a confirmation instead of silently discarding the edits
    private readonly persistedSnapshot = signal<string>('');

    private readonly buildConfigurationComponent = viewChild(ProgrammingExerciseBuildConfigurationComponent);

    /** phase names only have to be unique within their container, as containers execute independently of each other */
    readonly arePhaseNamesValid = computed(() =>
        this.containers().every((container) => {
            const normalizedNames = container.phases.map((phase) => phase.name.toLowerCase());
            const namesAreUnique = new Set(normalizedNames).size === normalizedNames.length;
            const namesArePatternValid = container.phases.every((phase) => BUILD_PHASE_NAME_PATTERN.test(phase.name));
            const namesAreNotReserved = normalizedNames.every((name) => !BUILD_PHASE_RESERVED_NAMES.has(name));
            return namesAreUnique && namesArePatternValid && namesAreNotReserved;
        }),
    );

    readonly areContainerNamesValid = computed(() => {
        const containers = this.containers();
        const normalizedNames = containers.map((container) => container.name.toLowerCase());
        const namesAreUnique = new Set(normalizedNames).size === normalizedNames.length;
        return namesAreUnique && containers.every((container) => BUILD_CONTAINER_NAME_PATTERN.test(container.name));
    });

    // the timeout bounds come from the build configuration child once it has read the profile info; exposed so the
    // template can render the valid range in the out-of-bounds message
    readonly timeoutMinValue = computed(() => this.buildConfigurationComponent()?.timeoutMinValue());
    readonly timeoutMaxValue = computed(() => this.buildConfigurationComponent()?.timeoutMaxValue());

    readonly hasPhases = computed(() => this.containers().every((container) => container.phases.length > 0));

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

    // before the build configuration child exists, do not block saving on its resource limits
    readonly areDockerResourcesValid = computed(() => this.buildConfigurationComponent()?.areDockerResourcesValid() ?? true);

    // the Docker flags are assembled by the build configuration child, so the size check lives there and is delegated here
    readonly areDockerFlagsWithinSizeLimit = computed(() => this.buildConfigurationComponent()?.areDockerFlagsWithinSizeLimit() ?? true);

    readonly isBuildPlanConfigurationWithinSizeLimit = computed(() => JSON.stringify({ containers: this.containers() }).length <= BUILD_PLAN_CONFIGURATION_MAX_LENGTH);

    // An empty container image is allowed: submit() sends no image for it and the build falls back to the exercise's
    // language default per container, so the container keeps following default image bumps instead of pinning one.
    readonly canSubmit = computed(
        () =>
            this.containers().length > 0 &&
            this.hasPhases() &&
            this.areContainerNamesValid() &&
            this.arePhaseNamesValid() &&
            this.isTimeoutValid() &&
            this.areDockerResourcesValid() &&
            this.isBuildPlanConfigurationWithinSizeLimit() &&
            this.areDockerFlagsWithinSizeLimit(),
    );

    /**
     * Appends a container that checks out the repositories configured on the exercise, i.e. one that does not scope its
     * repositories, so that adding a container does not silently change what an existing build plan checks out.
     */
    addContainer(): void {
        // the new container reuses the first container's image; when that one inherits the language default, so does this one
        this.containers.update((containers) => [...containers, { name: '', dockerImage: containers[0]?.dockerImage, phases: [] }]);
    }

    removeContainer(index: number): void {
        this.containers.update((containers) => containers.filter((_, currentIndex) => currentIndex !== index));
    }

    updateContainer(index: number, container: BuildContainer): void {
        this.containers.update((containers) => containers.map((current, currentIndex) => (currentIndex === index ? container : current)));
    }

    /** the names of all other containers, so that a container can detect a duplicate name */
    otherContainerNames(index: number): string[] {
        return this.containers()
            .filter((_, currentIndex) => currentIndex !== index)
            .map((container) => container.name);
    }

    ngOnInit(): void {
        this.activatedRoute.data.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(({ exercise }) => {
            this.initEditingState(exercise);
            this.programmingExercise.set(exercise);
            // safe to read the fields here: this runs synchronously right after they were initialized from the exercise
            this.persistedSnapshot.set(this.snapshot());
            this.seedDefaultsFromTemplate(exercise);
            this.loadParticipationsWithResults(exercise);
        });
    }

    /**
     * Seeds the editor with the language default plan when the exercise has no build plan configuration yet (a null
     * configuration builds on the language default at build time), so it opens with the real default plan instead of an
     * empty list. The seeded plan becomes one default container. Does nothing when the editor already has containers or
     * the exercise has no programming language. The response is only applied while it still fits the editor state that
     * asked for it, so neither a slow response for a previously opened exercise nor one overtaken by the instructor's own
     * edits can overwrite what is on screen.
     */
    private seedDefaultsFromTemplate(exercise: ProgrammingExercise): void {
        const programmingLanguage = exercise.programmingLanguage;
        if (!programmingLanguage || this.containers().length > 0) {
            return;
        }
        // the field values as they stand before the request: only the seeded containers may later be folded into the
        // baseline, so an edit made while the template is loading stays unsaved instead of being marked as persisted
        const baselineTimeout = this.timeout();
        const baselineDockerFlags = this.programmingExercise()?.buildConfig?.dockerFlags;
        this.buildPhasesTemplateService
            // the static analysis and sequential run settings select a different template file on the server, so they have
            // to be passed here as well; otherwise a seeded plan differs from the one Local CI would build the exercise with
            .getTemplate(!!exercise.exerciseGroup, programmingLanguage, exercise.projectType, exercise.staticCodeAnalysisEnabled, exercise.buildConfig?.sequentialTestRuns)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (template) => {
                    if (this.programmingExercise()?.id !== exercise.id) {
                        return;
                    }
                    if (template.dockerImage) {
                        this.defaultDockerImage.set(template.dockerImage);
                    }
                    // re-check the containers here: the instructor may have authored one while the request was in flight
                    if (template.phases?.length && this.containers().length === 0) {
                        // the image stays unset so the seeded container inherits; the template's image is only the placeholder
                        const seeded = [{ name: DEFAULT_BUILD_CONTAINER_NAME, phases: template.phases }];
                        this.containers.set(seeded);
                        // the seeded containers are not a user edit, so they are folded into the baseline; every other field
                        // keeps its pre-request value, which leaves an edit made in the meantime dirty
                        this.persistedSnapshot.set(this.snapshotOf(seeded, baselineTimeout, baselineDockerFlags));
                    }
                },
                // the editor stays usable without the template; the instructor can still author the plan manually
                error: () => {},
            });
    }

    /**
     * Initializes the editable build plan state (containers and timeout) from the exercise's build config. A build plan
     * that carries a flat list of phases is normalized into a single container, so that the editor only deals with
     * containers.
     */
    private initEditingState(exercise: ProgrammingExercise): void {
        const buildConfig = exercise.buildConfig;
        this.timeout.set(buildConfig?.timeoutSeconds ?? 0);

        const configJson = buildConfig?.buildPlanConfiguration;
        const parsed = parseBuildPlanPhases(configJson);
        const containers = effectiveContainers(parsed);
        if (containers.length) {
            this.containers.set(containers);
            return;
        }

        // No structured phases yet: convert a legacy build script or older configuration format so an existing exercise
        // keeps its build plan instead of opening with an empty editor (which would overwrite the script on save).
        const converted = this.legacyBuildPlanConverterService.convertLegacyBuildPlanConfiguration(buildConfig?.buildScript, configJson);
        this.containers.set(
            converted?.phases?.length ? [{ name: DEFAULT_BUILD_CONTAINER_NAME, dockerImage: converted.dockerImage ?? parsed?.dockerImage ?? '', phases: converted.phases }] : [],
        );
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
        // the fields stay editable while the request runs, so the state that is actually sent is captured here and only
        // that state becomes the baseline; an edit made in the meantime therefore stays unsaved instead of being marked
        // as persisted and silently discarded on the next navigation
        const submittedSnapshot = this.snapshot();
        this.buildPlanConfigurationService
            .updateBuildPlanConfiguration(exercise.id, {
                // a blank image is trimmed to undefined, so the container inherits the language default instead of
                // persisting an unusable empty image (mirrors the plan-level behaviour the reviewed editor had)
                buildPlan: { containers: this.containers().map((container) => cloneWith(container, { dockerImage: container.dockerImage?.trim() || undefined })) },
                timeoutSeconds: this.timeout(),
                dockerFlags: exercise.buildConfig?.dockerFlags,
            })
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.isSaving.set(false);
                    this.persistedSnapshot.set(submittedSnapshot);
                    this.alertService.success('artemisApp.programmingExercise.buildPlanConfiguration.saved');
                },
                error: (error) => {
                    this.isSaving.set(false);
                    onError(this.alertService, error);
                },
            });
    }

    /**
     * Serializes everything {@link submit} persists so it can be compared against the persisted baseline. The Docker flags
     * are included because the build configuration child edits them in place on the exercise, so leaving them out would
     * let env variable, network and resource limit edits be discarded without a warning.
     */
    private snapshot(): string {
        return this.snapshotOf(this.containers(), this.timeout(), this.programmingExercise()?.buildConfig?.dockerFlags);
    }

    /**
     * Serializes one specific combination of editable values. An asynchronous callback has to build its baseline through
     * this method and name the values it actually persisted: taking {@link snapshot} there would claim every field the
     * instructor edited while the request was running, and those edits would then be discarded without a warning.
     */
    private snapshotOf(containers: BuildContainer[], timeout: number, dockerFlags: string | undefined): string {
        return JSON.stringify({ containers, timeout, dockerFlags });
    }

    /**
     * Allows leaving the page without a prompt only when there are no unsaved edits (the current state still matches the
     * persisted baseline). The pending changes guard on the route shows the confirmation when this returns false.
     */
    canDeactivate(): boolean {
        return this.snapshot() === this.persistedSnapshot();
    }

    /**
     * Displays the alert for confirming refreshing or closing the page if there are unsaved changes
     * NOTE: while the beforeunload event might be deprecated in the future, it is currently the only way to display a confirmation dialog when the user tries to leave the page
     * @param event the beforeunload event
     */
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification(event: BeforeUnloadEvent) {
        if (!this.canDeactivate()) {
            event.preventDefault();
            return this.translateService.instant('pendingChanges');
        }
        return true;
    }
}
