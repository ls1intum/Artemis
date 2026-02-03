import { Component, HostBinding, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { ExternalCloningService } from 'app/programming/shared/services/external-cloning.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { hasExerciseDueDatePassed, isResumeExerciseAvailable, isStartExerciseAvailable, isStartPracticeAvailable } from 'app/exercise/util/exercise.utils';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { finalize } from 'rxjs/operators';
import { faEye, faFolderOpen, faPlayCircle, faRedo, faUsers } from '@fortawesome/free-solid-svg-icons';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import dayjs from 'dayjs/esm';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { PROFILE_ATHENA } from 'app/app.constants';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ButtonType } from 'app/shared/components/buttons/button/button.component';
import { NgTemplateOutlet } from '@angular/common';
import { ExerciseActionButtonComponent } from 'app/shared/components/buttons/exercise-action-button/exercise-action-button.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { CodeButtonComponent } from 'app/shared/components/buttons/code-button/code-button.component';
import { RequestFeedbackButtonComponent } from '../request-feedback-button/request-feedback-button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { StartPracticeModeButtonComponent } from 'app/core/course/overview/exercise-details/start-practice-mode-button/start-practice-mode-button.component';
import { OpenCodeEditorButtonComponent } from 'app/core/course/overview/exercise-details/open-code-editor-button/open-code-editor-button.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ArtemisQuizService } from 'app/quiz/shared/service/quiz.service';
import { HttpErrorResponse } from '@angular/common/http';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';

@Component({
    imports: [
        NgTemplateOutlet,
        ExerciseActionButtonComponent,
        RouterLink,
        NgbTooltip,
        FeatureToggleDirective,
        StartPracticeModeButtonComponent,
        OpenCodeEditorButtonComponent,
        CodeButtonComponent,
        RequestFeedbackButtonComponent,
        ArtemisTranslatePipe,
    ],
    providers: [ExternalCloningService],
    selector: 'jhi-exercise-details-student-actions',
    templateUrl: './exercise-details-student-actions.component.html',
    styleUrls: ['../../course-overview/course-overview.scss'],
})
export class ExerciseDetailsStudentActionsComponent {
    protected readonly faFolderOpen = faFolderOpen;
    protected readonly faUsers = faUsers;
    protected readonly faEye = faEye;
    protected readonly faPlayCircle = faPlayCircle;
    protected readonly faRedo = faRedo;

    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ExerciseType = ExerciseType;
    protected readonly InitializationState = InitializationState;
    protected readonly ButtonType = ButtonType;

    private alertService = inject(AlertService);
    private courseExerciseService = inject(CourseExerciseService);
    private participationService = inject(ParticipationService);
    private profileService = inject(ProfileService);

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
                this.initializeExerciseSpecificState(exercise);
            });
        });
    }

    @HostBinding('class.col')
    readonly equalColumns = input(true);
    @HostBinding('class.col-auto')
    readonly smallColumns = input(false);

    readonly exercise = input<Exercise>(undefined!);
    readonly courseId = input<number>(undefined!);
    readonly smallButtons = input<boolean>(undefined!);
    readonly examMode = input<boolean>(undefined!);
    readonly isGeneratingFeedback = input<boolean>(undefined!);

    readonly generatingFeedback = output<void>();

    private readonly _uninitializedQuiz = signal(false);
    private readonly _quizNotStarted = signal(false);
    private readonly _gradedParticipation = signal<StudentParticipation | undefined>(undefined);
    private readonly _practiceParticipation = signal<StudentParticipation | undefined>(undefined);
    private readonly _programmingExercise = signal<ProgrammingExercise | undefined>(undefined);
    private readonly _isTeamAvailable = signal(false);
    private readonly _hasRatedGradedResult = signal(false);
    private readonly _editorLabel = signal<string | undefined>(undefined);
    private readonly _numberOfGradedParticipationResults = signal(0);
    private readonly _isLoading = signal(false);
    private readonly _studentParticipations = signal<StudentParticipation[]>([]);

    readonly uninitializedQuiz = computed(() => this._uninitializedQuiz());
    readonly quizNotStarted = computed(() => this._quizNotStarted());
    readonly gradedParticipation = computed(() => this._gradedParticipation());
    readonly practiceParticipation = computed(() => this._practiceParticipation());
    readonly programmingExercise = computed(() => this._programmingExercise());
    readonly isTeamAvailable = computed(() => this._isTeamAvailable());
    readonly hasRatedGradedResult = computed(() => this._hasRatedGradedResult());
    readonly editorLabel = computed(() => this._editorLabel());
    readonly numberOfGradedParticipationResults = computed(() => this._numberOfGradedParticipationResults());
    readonly isLoading = computed(() => this._isLoading());
    readonly studentParticipations = computed(() => this._studentParticipations());

    readonly athenaEnabled = this.profileService.isProfileActive(PROFILE_ATHENA);

    readonly beforeDueDate = computed(() => {
        const exercise = this.exercise();
        return !exercise.dueDate || !hasExerciseDueDatePassed(exercise, this._gradedParticipation());
    });

    private initializeExerciseSpecificState(exercise: Exercise): void {
        if (exercise.type === ExerciseType.QUIZ) {
            const quizExercise = exercise as QuizExercise;
            this._uninitializedQuiz.set(ArtemisQuizService.isUninitialized(quizExercise));
            this._quizNotStarted.set(ArtemisQuizService.notStarted(quizExercise));
        } else if (exercise.type === ExerciseType.PROGRAMMING) {
            this._programmingExercise.set(exercise as ProgrammingExercise);
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
        return !this.examMode() && isStartPracticeAvailable(this.exercise(), this._practiceParticipation());
    }

    startExercise() {
        this._isLoading.set(true);
        const programmingExercise = this._programmingExercise();
        this.courseExerciseService
            .startExercise(this.exercise().id!)
            .pipe(finalize(() => this._isLoading.set(false)))
            .subscribe({
                next: (participation) => {
                    if (participation) {
                        this.receiveNewParticipation(participation);
                    }
                    if (programmingExercise) {
                        if (participation?.initializationState === InitializationState.INITIALIZED) {
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
            .resumeProgrammingExercise(this.exercise().id!, participation!.id!)
            .pipe(finalize(() => this._isLoading.set(false)))
            .subscribe({
                next: (resumedParticipation: StudentParticipation) => {
                    if (resumedParticipation) {
                        // Otherwise the client would think that all results are loaded, but there would not be any (=> no graded result).
                        const currentParticipations = this._studentParticipations();
                        const replacedIndex = currentParticipations.indexOf(participation!);
                        const updatedParticipations = [...currentParticipations];
                        updatedParticipations[replacedIndex] = resumedParticipation;
                        this._studentParticipations.set(updatedParticipations);
                        this.updateParticipations();
                        this.alertService.success('artemisApp.exercise.resumeProgrammingExercise');
                    }
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
        return participations?.length ? participations[0].team?.id : this.exercise().studentAssignedTeamId;
    }

    get allowEditing(): boolean {
        const gradedParticipation = this._gradedParticipation();
        return (
            (gradedParticipation?.initializationState === InitializationState.INITIALIZED && this.beforeDueDate()) ||
            gradedParticipation?.initializationState === InitializationState.FINISHED
        );
    }
}
