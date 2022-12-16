import { Component, ContentChild, HostBinding, Input, OnChanges, OnInit, TemplateRef } from '@angular/core';
import { Router } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { HttpClient } from '@angular/common/http';
import { SourceTreeService } from 'app/exercises/programming/shared/service/sourceTree.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { InitializationState } from 'app/entities/participation/participation.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { isResumeExerciseAvailable, isStartExerciseAvailable, isStartPracticeAvailable } from 'app/exercises/shared/exercise/exercise.utils';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ArtemisQuizService } from 'app/shared/quiz/quiz.service';
import { finalize } from 'rxjs/operators';
import { faComment, faExternalLinkAlt, faEye, faFolderOpen, faPlayCircle, faRedo, faUsers } from '@fortawesome/free-solid-svg-icons';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { TranslateService } from '@ngx-translate/core';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import dayjs from 'dayjs/esm';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

@Component({
    selector: 'jhi-exercise-details-student-actions',
    templateUrl: './exercise-details-student-actions.component.html',
    styleUrls: ['../course-overview.scss'],
    providers: [SourceTreeService],
})
export class ExerciseDetailsStudentActionsComponent implements OnInit, OnChanges {
    readonly FeatureToggle = FeatureToggle;
    readonly ExerciseType = ExerciseType;
    readonly InitializationState = InitializationState;
    readonly dayjs = dayjs;

    @Input() @HostBinding('class.col') equalColumns = true;
    @Input() @HostBinding('class.col-auto') smallColumns = false;

    @Input() exercise: Exercise;
    @Input() courseId: number;
    @Input() actionsOnly: boolean;
    @Input() smallButtons: boolean;
    @Input() showResult: boolean;
    @Input() examMode: boolean;

    // extension points, see shared/extension-point
    @ContentChild('overrideCloneOnlineEditorButton') overrideCloneOnlineEditorButton: TemplateRef<any>;

    uninitializedQuiz: boolean;
    quizNotStarted: boolean;
    gradedParticipation?: StudentParticipation;
    practiceParticipation?: StudentParticipation;
    programmingExercise?: ProgrammingExercise;

    // Icons
    faComment = faComment;
    faFolderOpen = faFolderOpen;
    faUsers = faUsers;
    faEye = faEye;
    faPlayCircle = faPlayCircle;
    faRedo = faRedo;
    faExternalLinkAlt = faExternalLinkAlt;

    constructor(
        private alertService: AlertService,
        private courseExerciseService: CourseExerciseService,
        private httpClient: HttpClient,
        private router: Router,
        private translateService: TranslateService,
        private participationService: ParticipationService,
    ) {}

    ngOnInit(): void {
        if (this.exercise.type === ExerciseType.QUIZ) {
            const quizExercise = this.exercise as QuizExercise;
            this.uninitializedQuiz = ArtemisQuizService.isUninitialized(quizExercise);
            this.quizNotStarted = ArtemisQuizService.notStarted(quizExercise);
        } else if (this.exercise.type === ExerciseType.PROGRAMMING) {
            this.programmingExercise = this.exercise as ProgrammingExercise;
        }
    }

    ngOnChanges() {
        const studentParticipations = this.exercise.studentParticipations ?? [];
        this.gradedParticipation = this.participationService.getSpecificStudentParticipation(studentParticipations, false);
        this.practiceParticipation = this.participationService.getSpecificStudentParticipation(studentParticipations, true);
    }

    /**
     * Starting an exercise is not possible in the exam, otherwise see exercise.utils -> isStartExerciseAvailable
     */
    isStartExerciseAvailable(): boolean {
        return !this.examMode && isStartExerciseAvailable(this.exercise);
    }

    /**
     * Resuming an exercise is not possible in the exam, otherwise see exercise.utils -> isResumeExerciseAvailable
     */
    isResumeExerciseAvailable(): boolean {
        return !this.examMode && isResumeExerciseAvailable(this.exercise, this.gradedParticipation);
    }

    /**
     * Practicing an exercise is not possible in the exam, otherwise see exercise.utils -> isStartPracticeAvailable
     */
    isStartPracticeAvailable(): boolean {
        return !this.examMode && isStartPracticeAvailable(this.exercise);
    }

    startExercise() {
        if (this.exercise.type === ExerciseType.QUIZ) {
            // Start the quiz
            return this.router.navigate(['/courses', this.courseId, 'quiz-exercises', this.exercise.id, 'live']);
        }
        this.exercise.loading = true;
        this.courseExerciseService
            .startExercise(this.exercise.id!)
            .pipe(finalize(() => (this.exercise.loading = false)))
            .subscribe({
                next: (participation) => {
                    if (participation) {
                        this.exercise.studentParticipations = [...(this.exercise.studentParticipations ?? []), participation];
                        this.gradedParticipation = participation;
                    }
                    if (this.programmingExercise) {
                        if (this.programmingExercise.allowOfflineIde) {
                            this.alertService.success('artemisApp.exercise.personalRepositoryClone');
                        } else {
                            this.alertService.success('artemisApp.exercise.personalRepositoryOnline');
                        }
                    }
                },
                error: () => {
                    this.alertService.warning('artemisApp.exercise.startError');
                },
            });
    }

    /**
     * resume the programming exercise
     */
    resumeProgrammingExercise(testRun: boolean) {
        this.exercise.loading = true;
        const participation = this.participationService.getSpecificStudentParticipation(this.exercise.studentParticipations!, testRun);
        this.courseExerciseService
            .resumeProgrammingExercise(this.exercise.id!, participation!.id!)
            .pipe(finalize(() => (this.exercise.loading = false)))
            .subscribe({
                next: (resumedParticipation: StudentParticipation) => {
                    if (resumedParticipation) {
                        // Otherwise the client would think that all results are loaded, but there would not be any (=> no graded result).
                        resumedParticipation.results = participation ? participation.results : [];
                        const replacedIndex = this.exercise.studentParticipations!.indexOf(participation!);
                        this.exercise.studentParticipations![replacedIndex] = resumedParticipation;
                        this.alertService.success('artemisApp.exercise.resumeProgrammingExercise');
                    }
                },
                error: (error) => {
                    this.alertService.error(`artemisApp.${error.error.entityName}.errors.${error.error.errorKey}`);
                },
            });
    }

    private feedbackSent = false;

    isFeedbackRequestButtonDisabled(): boolean {
        const showUngradedResults = true;
        const latestResult = this.gradedParticipation?.results && this.gradedParticipation.results.find(({ rated }) => showUngradedResults || rated === true);
        const allHiddenTestsPassed = latestResult?.score !== undefined && latestResult.score >= 100;

        const requestAlreadySent = (this.gradedParticipation?.individualDueDate && this.gradedParticipation.individualDueDate.isBefore(Date.now())) ?? false;

        return !allHiddenTestsPassed || requestAlreadySent || this.feedbackSent;
    }

    requestFeedback() {
        const confirmLockRepository = this.translateService.instant('artemisApp.exercise.lockRepositoryWarning');
        if (!window.confirm(confirmLockRepository)) {
            return;
        }

        this.courseExerciseService.requestFeedback(this.exercise.id!).subscribe({
            next: (participation: StudentParticipation) => {
                if (participation) {
                    this.feedbackSent = true;
                    this.alertService.success('artemisApp.exercise.feedbackRequestSent');
                }
            },
            error: (error) => {
                this.alertService.error(`artemisApp.${error.error.entityName}.errors.${error.error.errorKey}`);
            },
        });
    }

    get isBeforeStartDateAndStudent(): boolean {
        return !this.exercise.isAtLeastTutor && !!this.exercise.startDate && dayjs().isBefore(this.exercise.startDate);
    }

    /**
     * Display the 'open code editor' or 'clone repo' buttons if
     * - the participation is initialized (build plan exists, this is always the case during an exam), or
     * - the participation is inactive (build plan cleaned up), but can not be resumed (e.g. because we're after the due date)
     */
    public shouldDisplayIDEButtons(): boolean {
        return !!this.exercise.studentParticipations?.some((participation) => {
            const startExerciseNotAvailable = !isStartExerciseAvailable(this.exercise) && !participation.testRun;
            const startPracticeNotAvailable = !isStartPracticeAvailable(this.exercise) && participation.testRun;
            return (
                participation.initializationState === InitializationState.INITIALIZED ||
                (participation.initializationState === InitializationState.INACTIVE && (startExerciseNotAvailable || startPracticeNotAvailable))
            );
        });
    }

    /**
     * Returns the id of the team that the student is assigned to (only applicable to team-based exercises)
     *
     * @return {assignedTeamId}
     */
    get assignedTeamId(): number | undefined {
        const participations = this.exercise.studentParticipations;
        return participations?.length ? participations[0].team?.id : this.exercise.studentAssignedTeamId;
    }

    buildPlanUrl(participation: StudentParticipation) {
        return (participation as ProgrammingExerciseStudentParticipation).buildPlanUrl;
    }
}
