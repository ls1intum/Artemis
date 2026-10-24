import { Directive, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { hasExerciseDueDatePassed, isResumeExerciseAvailable, isStartExerciseAvailable, isStartPracticeAvailable } from 'app/exercise/util/exercise.utils';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { finalize } from 'rxjs/operators';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import dayjs from 'dayjs/esm';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { MODULE_FEATURE_ATHENA } from 'app/app.constants';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ArtemisQuizService } from 'app/quiz/shared/service/quiz.service';
import { HttpErrorResponse } from '@angular/common/http';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';

/** Shared participation state and student exercise actions used by both exercise action surfaces. */
@Directive()
export abstract class StudentExerciseActionsDirective {
    private alertService = inject(AlertService);
    private courseExerciseService = inject(CourseExerciseService);
    private participationService = inject(ParticipationService);
    protected readonly profileService = inject(ProfileService);

    constructor() {
        effect(() => {
            const exercise = this.exercise();
            untracked(() => {
                if (!exercise) {
                    return;
                }
                // Initialize local participations from the exercise input
                this._studentParticipations.set(exercise.studentParticipations ?? []);
                this.updateParticipations();
                this._isTeamAvailable.set(!!(exercise.teamMode && exercise.studentAssignedTeamIdComputed && exercise.studentAssignedTeamId));
                this.resetExerciseSpecificState();
                this.initializeExerciseSpecificState(exercise);
            });
        });
    }

    readonly exercise = input.required<Exercise>();
    readonly courseId = input.required<number>();
    readonly smallButtons = input<boolean>(false);
    readonly examMode = input<boolean>(false);

    readonly newParticipation = output<StudentParticipation>();

    protected readonly _uninitializedQuiz = signal(false);
    protected readonly _quizNotStarted = signal(false);
    protected readonly _gradedParticipation = signal<StudentParticipation | undefined>(undefined);
    protected readonly _practiceParticipation = signal<StudentParticipation | undefined>(undefined);
    protected readonly _programmingExercise = signal<ProgrammingExercise | undefined>(undefined);
    protected readonly _isTeamAvailable = signal(false);
    protected readonly _hasRatedGradedResult = signal(false);
    protected readonly _editorLabel = signal<string | undefined>(undefined);
    protected readonly _numberOfGradedParticipationResults = signal(0);
    protected readonly _isLoading = signal(false);
    protected readonly _studentParticipations = signal<StudentParticipation[]>([]);

    readonly uninitializedQuiz = this._uninitializedQuiz.asReadonly();
    readonly quizNotStarted = this._quizNotStarted.asReadonly();
    readonly gradedParticipation = this._gradedParticipation.asReadonly();
    readonly practiceParticipation = this._practiceParticipation.asReadonly();
    readonly programmingExercise = this._programmingExercise.asReadonly();
    readonly isTeamAvailable = this._isTeamAvailable.asReadonly();
    readonly hasRatedGradedResult = this._hasRatedGradedResult.asReadonly();
    readonly editorLabel = this._editorLabel.asReadonly();
    readonly numberOfGradedParticipationResults = this._numberOfGradedParticipationResults.asReadonly();
    readonly isLoading = this._isLoading.asReadonly();
    readonly studentParticipations = this._studentParticipations.asReadonly();
    readonly hasGradedSubmission = computed(() => !!this._gradedParticipation()?.submissions?.some((submission) => submission.submitted));

    readonly athenaEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATHENA);

    readonly beforeDueDate = computed(() => {
        const exercise = this.exercise();
        return !exercise.dueDate || !hasExerciseDueDatePassed(exercise, this._gradedParticipation());
    });

    protected resetExerciseSpecificState(): void {}

    private initializeExerciseSpecificState(exercise: Exercise): void {
        if (exercise.type === ExerciseType.QUIZ) {
            const quizExercise = exercise as QuizExercise;
            this._uninitializedQuiz.set(ArtemisQuizService.isUninitialized(quizExercise));
            this._quizNotStarted.set(ArtemisQuizService.notStarted(quizExercise));
        } else if (exercise.type === ExerciseType.PROGRAMMING) {
            this._programmingExercise.set(exercise);
        } else if (exercise.type === ExerciseType.MODELING) {
            this._editorLabel.set('openModelingEditor');
        } else if (exercise.type === ExerciseType.TEXT) {
            this._editorLabel.set('openTextEditor');
        } else if (exercise.type === ExerciseType.FILE_UPLOAD) {
            this._editorLabel.set('uploadFile');
        }
    }

    receiveNewParticipation(newParticipation: StudentParticipation) {
        const currentParticipations = this._studentParticipations();
        let updatedParticipations: StudentParticipation[];
        if (currentParticipations.map((participation) => participation.id).includes(newParticipation.id)) {
            updatedParticipations = currentParticipations.map((participation) => (participation.id === newParticipation.id ? newParticipation : participation));
        } else {
            updatedParticipations = [...currentParticipations, newParticipation];
        }
        this._studentParticipations.set(updatedParticipations);
        this.updateParticipations();
        this.newParticipation.emit(newParticipation);
    }

    updateParticipations() {
        const currentParticipations = this._studentParticipations();
        const gradedParticipation = this.participationService.getSpecificStudentParticipation(currentParticipations, false);
        this._gradedParticipation.set(gradedParticipation);
        this._numberOfGradedParticipationResults.set(getAllResultsOfAllSubmissions(gradedParticipation?.submissions).length);
        this._practiceParticipation.set(this.participationService.getSpecificStudentParticipation(currentParticipations, true));

        this._hasRatedGradedResult.set(
            !!getAllResultsOfAllSubmissions(gradedParticipation?.submissions)?.some((result) => result.rated === true && result.assessmentType !== AssessmentType.AUTOMATIC_ATHENA),
        );
    }

    /**
     * Starting an exercise is not possible in the exam or if it's a team exercise and the student is not yet assigned a team, otherwise see exercise.utils ->
     * isStartExerciseAvailable
     */
    isStartExerciseAvailable(): boolean {
        const exercise = this.exercise();
        const individualExerciseOrTeamAssigned = !!(!exercise.teamMode || exercise.studentAssignedTeamId);
        return !this.examMode() && individualExerciseOrTeamAssigned && isStartExerciseAvailable(this.exercise(), this._gradedParticipation());
    }

    /**
     * Resuming an exercise is not possible in the exam, otherwise see exercise.utils -> isResumeExerciseAvailable
     */
    isResumeExerciseAvailable(participation?: StudentParticipation): boolean {
        return !this.examMode() && isResumeExerciseAvailable(this.exercise(), participation);
    }

    /**
     * Practicing an exercise is not possible in the exam, otherwise see exercise.utils -> isStartPracticeAvailable
     */
    isStartPracticeAvailable(): boolean {
        return !this.examMode() && isStartPracticeAvailable(this.exercise(), this._practiceParticipation(), this._gradedParticipation());
    }

    startExercise() {
        // The button is only disabled after the next render, so ignore a second click that arrives before that
        if (this._isLoading()) {
            return;
        }
        this._isLoading.set(true);
        const programmingExercise = this._programmingExercise();
        this.courseExerciseService
            .startExercise(this.exercise().id!, this.exercise())
            .pipe(finalize(() => this._isLoading.set(false)))
            .subscribe({
                next: (participation) => {
                    this.receiveNewParticipation(participation);
                    if (programmingExercise) {
                        if (participation.initializationState === InitializationState.INITIALIZED) {
                            if (programmingExercise.allowOfflineIde) {
                                this.alertService.success('artemisApp.exercise.personalRepositoryClone');
                            } else {
                                this.alertService.success('artemisApp.exercise.personalRepositoryOnline');
                            }
                        } else {
                            this.alertService.error('artemisApp.exercise.startError');
                        }
                    }
                },
                error: (err: HttpErrorResponse) => {
                    const responseCodesWithErrorKeySentByServer = [403];
                    if (!responseCodesWithErrorKeySentByServer.includes(err.status)) {
                        this.alertService.error('artemisApp.exercise.startError');
                    }
                },
            });
    }

    /**
     * resume the programming exercise
     */
    resumeProgrammingExercise(testRun: boolean) {
        this._isLoading.set(true);
        const participation = testRun ? this._practiceParticipation() : this._gradedParticipation();
        this.courseExerciseService
            .resumeProgrammingExercise(this.exercise().id!, participation!.id!, this.exercise())
            .pipe(finalize(() => this._isLoading.set(false)))
            .subscribe({
                next: (resumedParticipation: StudentParticipation) => {
                    this.receiveNewParticipation(resumedParticipation);
                    this.alertService.success('artemisApp.exercise.resumeProgrammingExercise');
                },
                error: (error) => {
                    this.alertService.error(`artemisApp.${error.error.entityName}.errors.${error.error.errorKey}`);
                },
            });
    }

    get isBeforeStartDateAndStudent(): boolean {
        const exercise = this.exercise();
        return !exercise.isAtLeastTutor && !!exercise.startDate && dayjs().isBefore(exercise.startDate);
    }

    /**
     * Display the 'open code editor' or 'code' buttons if
     * - the participation is initialized (build plan exists, this is always the case during an exam), or
     * - the participation is inactive (build plan cleaned up), but can not be resumed (e.g. because we're after the due date)
     *
     * for all conditions it is important that the repository is set
     *
     * For course exercises, an initialized practice participation should only be displayed if it's not possible to start a new graded participation.
     * For exam exercises, only one active participation can exist, so this should be shown.
     */
    public shouldDisplayIDEButtons(): boolean {
        if (!this.isRepositoryUriSet()) {
            return false;
        }
        const practiceParticipation = this._practiceParticipation();
        const gradedParticipation = this._gradedParticipation();
        const shouldPreferPractice = this.participationService.shouldPreferPractice(this.exercise());
        const activePracticeParticipation = practiceParticipation?.initializationState === InitializationState.INITIALIZED && (shouldPreferPractice || this.examMode());
        const activeGradedParticipation = gradedParticipation?.initializationState === InitializationState.INITIALIZED;
        const inactiveGradedParticipation =
            !!gradedParticipation?.initializationState &&
            [InitializationState.INACTIVE, InitializationState.FINISHED].includes(gradedParticipation.initializationState) &&
            !isStartExerciseAvailable(this.exercise(), gradedParticipation);

        return activePracticeParticipation || activeGradedParticipation || inactiveGradedParticipation;
    }

    /**
     * Returns true if the repository uri of the active participation is set
     * We don't want to show buttons that would interact with the repository if the repository is not set
     */
    private isRepositoryUriSet(): boolean {
        const participations = this._studentParticipations();
        const activeParticipation: ProgrammingExerciseStudentParticipation = this.participationService.getSpecificStudentParticipation(participations, false) ?? participations[0];
        return !!activeParticipation?.repositoryUri;
    }

    /**
     * Returns the id of the team that the student is assigned to (only applicable to team-based exercises)
     *
     * @return {assignedTeamId}
     */
    get assignedTeamId(): number | undefined {
        const participations = this._studentParticipations();
        // Fall through rather than branch: the course overview projects the participation without its team, and even
        // before that a team-mode participation could arrive without one. The exercise carries the resolved team id.
        return participations?.[0]?.team?.id ?? this.exercise().studentAssignedTeamId;
    }

    get allowEditing(): boolean {
        const gradedParticipation = this._gradedParticipation();
        return (
            (gradedParticipation?.initializationState === InitializationState.INITIALIZED && this.beforeDueDate()) ||
            gradedParticipation?.initializationState === InitializationState.FINISHED
        );
    }
}
