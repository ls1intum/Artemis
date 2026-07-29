import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, inject, input, output } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { RouterLink } from '@angular/router';
import { Course } from 'app/course/shared/entities/course.model';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { faBrain, faFolderOpen, faListAlt, faTable } from '@fortawesome/free-solid-svg-icons';
import { faFileCode } from '@fortawesome/free-regular-svg-icons';
import { ExerciseCacheService } from 'app/exercise/services/exercise-cache.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CodeButtonComponent } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { FeatureToggleLinkDirective } from 'app/foundation/feature-toggle/feature-toggle-link.directive';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { IrisReviewAssessmentButtonComponent } from 'app/iris/overview/understanding-assessment/shared/iris-assessment-button/iris-review-assessment-button.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { IrisAssessmentQuizService } from 'app/iris/overview/services/iris-assessment-quiz.service';
import { catchError, merge, of, switchMap, take } from 'rxjs';
import { map } from 'rxjs/operators';
import { QuizTimerBarComponent } from 'app/iris/overview/understanding-assessment/quiz-timer-bar/quiz-timer-bar.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';

interface AssessmentParticipationViewModel extends ProgrammingExerciseStudentParticipation {
    readonly participationLink?: Array<string | number>;
    readonly repositoryUri?: string;
    readonly repositoryViewLink?: Array<string | number>;
    readonly codeEditorLink?: Array<string | number>;
}

@Component({
    selector: 'jhi-iris-assessment-review-exercise',
    templateUrl: './iris-assessment-review-exercise.component.html',
    providers: [ExerciseCacheService],
    encapsulation: ViewEncapsulation.None,
    imports: [
        TranslateDirective,
        FaIconComponent,
        RouterLink,
        CodeButtonComponent,
        FeatureToggleLinkDirective,
        IrisReviewAssessmentButtonComponent,
        QuizTimerBarComponent,
        HelpIconComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisAssessmentReviewExerciseComponent {
    participations = input<ProgrammingExerciseStudentParticipation[]>([]);
    exercise = input.required<ProgrammingExercise>();
    course = input.required<Course>();
    isLoading = input(false);
    showStartInClassQuizButton = input(false);

    refresh = output<void>();

    protected readonly faFolderOpen = faFolderOpen;
    protected readonly faListAlt = faListAlt;
    protected readonly faBrain = faBrain;
    protected readonly faFileCode = faFileCode;
    protected readonly faTable = faTable;

    protected readonly RepositoryType = RepositoryType;
    protected readonly FeatureToggle = FeatureToggle;

    private profileService = inject(ProfileService);
    private assessmentQuizService = inject(IrisAssessmentQuizService);

    protected readonly localCIEnabled = this.profileService.isProfileActive(PROFILE_LOCALCI);
    private readonly exerciseId = computed(() => this.exercise().id);

    protected readonly activeInClassQuiz = toSignal(
        toObservable(this.exerciseId).pipe(
            switchMap((exerciseId) => {
                if (exerciseId === undefined) {
                    return of(undefined);
                }

                return merge(
                    this.assessmentQuizService.getActiveInClassQuiz(exerciseId).pipe(
                        map((response) => response.body ?? undefined),
                        catchError(() => of(undefined)),
                    ),
                    this.assessmentQuizService.currentInClassQuizForExercise(exerciseId),
                );
            }),
        ),
        { initialValue: undefined },
    );

    protected readonly inClassQuizButtonLabel = computed(() =>
        this.activeInClassQuiz() ? 'artemisApp.iris.assessmentInClassQuiz.restart' : 'artemisApp.iris.assessmentInClassQuiz.start',
    );

    protected readonly participationRows = computed<AssessmentParticipationViewModel[]>(() =>
        this.participations().map((participation) => ({
            ...participation,
            participationLink: [
                '/course-management',
                this.course().id!.toString(),
                `${this.exercise().type}-exercises`,
                this.exercise().id!.toString(),
                'participations',
                participation.id!.toString(),
                'submissions',
            ],
            repositoryUri: participation.userIndependentRepositoryUri,
        })),
    );

    startInClassQuiz(): void {
        const exerciseId = this.exercise().id;

        if (exerciseId === undefined) {
            return;
        }

        this.assessmentQuizService
            .startInClassQuiz(exerciseId)
            .pipe(take(1))
            .subscribe(() => this.refresh.emit());
    }

    handleInClassQuizTimerExpired(): void {
        const exerciseId = this.exercise().id;

        if (exerciseId !== undefined) {
            this.assessmentQuizService.clearActiveInClassQuiz(exerciseId);
        }
    }
}
