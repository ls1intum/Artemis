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
import { IrisReviewAssessmentButtonComponent } from 'app/iris/overview/ask-user/shared/iris-assessment-button/iris-review-assessment-button.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { of, switchMap, take } from 'rxjs';
import { QuizTimerBarComponent } from 'app/iris/overview/ask-user/quiz-timer-bar/quiz-timer-bar.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { IrisAssessmentReviewHttpService } from 'app/iris/overview/ask-user/services/iris-assessment-review-http.service';
import { hasDueDatePassed } from 'app/exercise/shared/entities/exercise/exercise.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { IrisInClassQuizStartWarningComponent } from 'app/iris/overview/ask-user/assessment-review-overview/iris-in-class-quiz-start-warning.component';

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
    private assessmentReviewService = inject(IrisAssessmentReviewHttpService);
    private modalService = inject(NgbModal);

    protected readonly localCIEnabled = this.profileService.isProfileActive(PROFILE_LOCALCI);
    private readonly exerciseId = computed(() => this.exercise().id);

    protected readonly availableInClassQuiz = toSignal(
        toObservable(this.exerciseId).pipe(
            switchMap((exerciseId) => {
                if (exerciseId === undefined) {
                    return of(undefined);
                }

                return this.assessmentReviewService.availableInClassQuizForExercise(exerciseId);
            }),
        ),
        { initialValue: undefined },
    );

    protected readonly inClassQuizButtonLabel = computed(() =>
        this.availableInClassQuiz() ? 'artemisApp.iris.assessmentInClassQuiz.restart' : 'artemisApp.iris.assessmentInClassQuiz.start',
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

    makeInClassQuizAvailable(): void {
        const exerciseId = this.exercise().id;

        if (exerciseId === undefined) {
            return;
        }

        if (!hasDueDatePassed(this.exercise())) {
            const modalRef = this.modalService.open(IrisInClassQuizStartWarningComponent, { size: 'lg', backdrop: 'static' });
            modalRef.componentInstance.confirmed.pipe(take(1)).subscribe(() => this.startInClassQuiz(exerciseId));
            return;
        }

        this.startInClassQuiz(exerciseId);
    }

    private startInClassQuiz(exerciseId: number): void {
        this.assessmentReviewService
            .makeInClassQuizAvailable(exerciseId)
            .pipe(take(1))
            .subscribe(() => this.refresh.emit());
    }

    handleInClassQuizTimerExpired(): void {
        const exerciseId = this.exercise().id;

        if (exerciseId !== undefined) {
            this.assessmentReviewService.clearActiveInClassQuiz(exerciseId);
        }
    }
}
