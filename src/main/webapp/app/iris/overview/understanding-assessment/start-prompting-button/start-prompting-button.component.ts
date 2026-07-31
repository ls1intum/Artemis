import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
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
    private readonly destroyRef = inject(DestroyRef);

    readonly exercise = input.required<Exercise>();
    readonly participation = input<StudentParticipation>();
    readonly smallButtons = input.required<boolean>();
    readonly hideLabelMobile = input(false);

    protected readonly FeatureToggle = FeatureToggle;
    protected readonly faBrain = faBrain;

    protected readonly isPromptingMode = signal(false);
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
        () => (this.latestEvent() !== IrisPipeEvent.BUILD_WITH_POINTS && this.quizAlreadyDoneFromServer()) || this.latestEvent() === IrisPipeEvent.PROMPTING_FINISHED,
    );

    protected readonly canBeStarted = computed(
        () =>
            (this.latestEvent() === IrisPipeEvent.BUILD_WITH_POINTS || this.latestSubmissionHasPoints()) &&
            !this.quizAlreadyDone() &&
            !this.isPromptingMode() &&
            !this.inClassPromptingModeStarted(),
    );

    protected readonly buttonLabel = computed(() => {
        if (this.quizAlreadyDone()) {
            return 'artemisApp.exerciseActions.prompting.finished';
        } else if (this.canBeStarted()) {
            return 'artemisApp.exerciseActions.prompting.start';
        } else if (this.isPromptingMode() || this.inClassPromptingModeStarted()) {
            return 'artemisApp.exerciseActions.prompting.currently';
        } else {
            return 'artemisApp.exerciseActions.prompting.noSubmission';
        }
    });

    constructor() {
        this.irisChatService
            .currentLatestEvent()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((event) => {
                if (event === IrisPipeEvent.PROMPTING_FINISHED) {
                    this.isPromptingMode.set(false);
                }
            });
    }

    protected startPromptingMode(): void {
        const exerciseId = this.exerciseId();
        if (!this.canBeStarted() || exerciseId === undefined) {
            return;
        }

        this.isPromptingMode.set(true);
        this.irisChatService.startPromptingMode(exerciseId).pipe(take(1)).subscribe();
    }
}
