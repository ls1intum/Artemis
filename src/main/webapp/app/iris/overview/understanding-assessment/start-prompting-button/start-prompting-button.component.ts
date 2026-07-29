import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { faBrain } from '@fortawesome/free-solid-svg-icons';
import { catchError, of, switchMap, take } from 'rxjs';

import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { IrisAssessmentQuizService } from 'app/iris/overview/services/iris-assessment-quiz.service';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { ExerciseActionButtonComponent } from 'app/shared-ui/components/buttons/exercise-action-button/exercise-action-button.component';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { IrisPipeEvent } from 'app/iris/shared/entities/iris-pipe-event.model';

@Component({
    selector: 'jhi-start-prompting-button',
    templateUrl: './start-prompting-button.component.html',
    imports: [ExerciseActionButtonComponent, FeatureToggleDirective, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisStartPromptingButtonComponent {
    private readonly irisChatService = inject(IrisChatService);
    private readonly assessmentQuizService = inject(IrisAssessmentQuizService);

    readonly exercise = input.required<Exercise>();
    readonly participation = input<StudentParticipation>();
    readonly smallButtons = input.required<boolean>();
    readonly hideLabelMobile = input(false);

    protected readonly FeatureToggle = FeatureToggle;
    protected readonly faBrain = faBrain;

    protected readonly isPromptingMode = signal(false);
    private readonly localQuizCompleted = signal(false);
    private readonly exerciseId = computed(() => this.exercise().id);

    private readonly latestSubmissionHasPoints = toSignal(
        toObservable(this.exerciseId).pipe(
            switchMap((exerciseId) => {
                if (exerciseId === undefined) {
                    return of(false);
                }

                return this.assessmentQuizService.latestSubmissionHasPoints(exerciseId).pipe(catchError(() => of(false)));
            }),
        ),
        { initialValue: false },
    );

    protected readonly latestEvent = toSignal(this.irisChatService.currentLatestEvent(), { initialValue: undefined });
    private readonly hasBuildWithPoints = computed(() => this.latestEvent() === IrisPipeEvent.BUILD_WITH_POINTS);
    private readonly promptingFinished = computed(() => this.latestEvent() === IrisPipeEvent.PROMPTING_FINISHED);
    private readonly activePromptingMode = computed(() => this.isPromptingMode() && !this.promptingFinished());

    private readonly quizAlreadyDoneFromServer = toSignal(
        toObservable(this.exerciseId).pipe(
            switchMap((exerciseId) => {
                if (exerciseId === undefined) {
                    return of(false);
                }

                return this.assessmentQuizService.isQuizAlreadyDone(exerciseId, false).pipe(catchError(() => of(false)));
            }),
        ),
        { initialValue: false },
    );

    private readonly inClassPromptingModeStarted = toSignal(
        toObservable(this.exerciseId).pipe(
            switchMap((exerciseId) => {
                if (exerciseId === undefined) {
                    return of(false);
                }

                return this.assessmentQuizService.currentStartedInClassQuizForExercise(exerciseId);
            }),
        ),
        { initialValue: false },
    );

    protected readonly quizAlreadyDone = computed(
        () => (!this.activePromptingMode() && !this.hasBuildWithPoints() && this.quizAlreadyDoneFromServer()) || this.localQuizCompleted(),
    );

    protected readonly canBeStarted = computed(
        () => (this.hasBuildWithPoints() || this.latestSubmissionHasPoints()) && !this.quizAlreadyDone() && !this.activePromptingMode() && !this.inClassPromptingModeStarted(),
    );

    protected readonly buttonLabel = computed(() => {
        if (this.activePromptingMode() || this.inClassPromptingModeStarted()) {
            return 'artemisApp.exerciseActions.prompting.currently';
        } else if (this.quizAlreadyDone()) {
            return 'artemisApp.exerciseActions.prompting.finished';
        } else if (this.canBeStarted()) {
            return 'artemisApp.exerciseActions.prompting.start';
        } else {
            return 'artemisApp.exerciseActions.prompting.noSubmission';
        }
    });

    constructor() {
        effect(() => {
            const latestEvent = this.latestEvent();
            if (latestEvent === IrisPipeEvent.BUILD_WITH_POINTS) {
                this.localQuizCompleted.set(false);
                this.isPromptingMode.set(false);
            } else if (latestEvent === IrisPipeEvent.PROMPTING_FINISHED) {
                this.localQuizCompleted.set(true);
                this.isPromptingMode.set(false);
            }
        });
    }

    protected startPromptingMode(): void {
        if (!this.canBeStarted()) {
            return;
        }

        const exerciseId = this.exerciseId();
        if (exerciseId === undefined) {
            return;
        }

        this.isPromptingMode.set(true);
        this.localQuizCompleted.set(false);
        this.irisChatService
            .startPromptingMode(exerciseId)
            .pipe(take(1))
            .subscribe({
                error: () => this.isPromptingMode.set(false),
            });
    }
}
