import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { faPlayCircle } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { BuildPlanConfigurationService } from 'app/programming/manage/services/build-plan-configuration.service';
import { BuildPhase, parseBuildPlanPhases } from 'app/programming/shared/entities/build-plan-phases.model';
import { ProgrammingExerciseBuildConfigurationComponent } from 'app/programming/manage/update/update-components/custom-build-plans/programming-exercise-build-configuration/programming-exercise-build-configuration.component';
import { BuildPhasesEditorComponent } from 'app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/build-phases-editor.component';

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
        NgbTooltip,
        FaIconComponent,
        HelpIconComponent,
        UpdatingResultComponent,
        ProgrammingExerciseBuildConfigurationComponent,
        BuildPhasesEditorComponent,
    ],
})
export class LocalCIBuildPlanEditorComponent implements OnInit {
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private buildPlanConfigurationService = inject(BuildPlanConfigurationService);
    private alertService = inject(AlertService);
    private activatedRoute = inject(ActivatedRoute);

    protected readonly farPlayCircle = faPlayCircle;

    readonly programmingExercise = signal<ProgrammingExercise | undefined>(undefined);
    readonly loadingResults = signal(true);
    readonly isSaving = signal(false);

    readonly phases = signal<BuildPhase[]>([]);
    readonly dockerImage = signal<string>('');
    readonly timeout = signal<number>(0);

    readonly isExamMode = computed(() => !!this.programmingExercise()?.exerciseGroup);

    ngOnInit(): void {
        this.activatedRoute.data.subscribe(({ exercise }) => {
            this.initEditingState(exercise);
            this.programmingExercise.set(exercise);
            this.loadParticipationsWithResults(exercise);
        });
    }

    /**
     * Initializes the editable build plan state (phases, Docker image, timeout) from the exercise's build config.
     */
    private initEditingState(exercise: ProgrammingExercise): void {
        const parsed = parseBuildPlanPhases(exercise.buildConfig?.buildPlanConfiguration);
        this.phases.set(parsed?.phases ?? []);
        this.dockerImage.set(parsed?.dockerImage ?? '');
        this.timeout.set(exercise.buildConfig?.timeoutSeconds ?? 0);
    }

    /**
     * Loads the template and solution participations with their latest results for the live build status, while keeping
     * the full build config of the resolved exercise (the participation query may not return it).
     */
    private loadParticipationsWithResults(resolvedExercise: ProgrammingExercise): void {
        this.programmingExerciseService.findWithTemplateAndSolutionParticipationAndLatestResults(resolvedExercise.id!).subscribe((response) => {
            const exercise = response.body!;
            exercise.buildConfig = resolvedExercise.buildConfig;
            this.programmingExercise.set(exercise);
            this.loadingResults.set(false);
        });
    }

    /**
     * Persists the edited build plan configuration via the dedicated build-config endpoint.
     */
    submit(): void {
        const exercise = this.programmingExercise();
        if (!exercise?.id) {
            return;
        }
        this.isSaving.set(true);
        this.buildPlanConfigurationService
            .updateBuildPlanConfiguration(exercise.id, {
                buildPlan: { phases: this.phases(), dockerImage: this.dockerImage() || undefined },
                timeoutSeconds: this.timeout(),
                dockerFlags: exercise.buildConfig?.dockerFlags,
            })
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
