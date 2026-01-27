import { Component, EventEmitter, HostBinding, Input, OnChanges, OnInit, Output, inject } from '@angular/core';
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
import { MODULE_FEATURE_ATHENA } from 'app/app.constants';
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
export class ExerciseDetailsStudentActionsComponent implements OnInit, OnChanges {
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

    @Input() @HostBinding('class.col') equalColumns = true;
    @Input() @HostBinding('class.col-auto') smallColumns = false;

    @Input() exercise: Exercise;
    @Input() courseId: number;
    @Input() smallButtons: boolean;
    @Input() examMode: boolean;
    @Input() isGeneratingFeedback: boolean;

    @Output() generatingFeedback: EventEmitter<void> = new EventEmitter<void>();

    uninitializedQuiz: boolean;
    quizNotStarted: boolean;
    gradedParticipation?: StudentParticipation;
    practiceParticipation?: StudentParticipation;
    programmingExercise?: ProgrammingExercise;
    isTeamAvailable: boolean;
    hasRatedGradedResult: boolean;
    beforeDueDate: boolean;
    editorLabel?: string;
    athenaEnabled = false;
    routerLink: string;
    numberOfGradedParticipationResults: number;

    ngOnInit(): void {
        this.athenaEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATHENA);

        if (this.exercise.type === ExerciseType.QUIZ) {
            const quizExercise = this.exercise as QuizExercise;
            this.uninitializedQuiz = ArtemisQuizService.isUninitialized(quizExercise);
            this.quizNotStarted = ArtemisQuizService.notStarted(quizExercise);
        } else if (this.exercise.type === ExerciseType.PROGRAMMING) {
            this.programmingExercise = this.exercise as ProgrammingExercise;
        } else if (this.exercise.type === ExerciseType.MODELING) {
            this.editorLabel = 'openModelingEditor';
        } else if (this.exercise.type === ExerciseType.TEXT) {
            this.editorLabel = 'openTextEditor';
        } else if (this.exercise.type === ExerciseType.FILE_UPLOAD) {
            this.editorLabel = 'uploadFile';
        }

        this.beforeDueDate = !this.exercise.dueDate || !hasExerciseDueDatePassed(this.exercise, this.gradedParticipation);
    }

    /**
     * Viewing the team is only possible if it's a team exercise and the student is already assigned to a team.
     */
    ngOnChanges() {
        this.updateParticipations();
        this.isTeamAvailable = !!(this.exercise.teamMode && this.exercise.studentAssignedTeamIdComputed && this.exercise.studentAssignedTeamId);
    }

    receiveNewParticipation(newParticipation: StudentParticipation) {
        const studentParticipations = this.exercise.studentParticipations ?? [];
        if (studentParticipations.map((participation) => participation.id).includes(newParticipation.id)) {
            this.exercise.studentParticipations = studentParticipations.map((participation) => (participation.id === newParticipation.id ? newParticipation : participation));
        } else {
            this.exercise.studentParticipations = [...studentParticipations, newParticipation];
        }
        this.updateParticipations();
    }

    updateParticipations() {
        const studentParticipations = this.exercise.studentParticipations ?? [];
        this.gradedParticipation = this.participationService.getSpecificStudentParticipation(studentParticipations, false);
        this.numberOfGradedParticipationResults = getAllResultsOfAllSubmissions(this.gradedParticipation?.submissions).length;
        this.practiceParticipation = this.participationService.getSpecificStudentParticipation(studentParticipations, true);

        this.hasRatedGradedResult = !!getAllResultsOfAllSubmissions(this.gradedParticipation?.submissions)?.some(
            (result) => result.rated === true && result.assessmentType !== AssessmentType.AUTOMATIC_ATHENA,
        );
    }

    /**
     * Starting an exercise is not possible in the exam or if it's a team exercise and the student is not yet assigned a team, otherwise see exercise.utils ->
     * isStartExerciseAvailable
     */
    isStartExerciseAvailable(): boolean {
        const individualExerciseOrTeamAssigned = !!(!this.exercise.teamMode || this.exercise.studentAssignedTeamId);
        return !this.examMode && individualExerciseOrTeamAssigned && isStartExerciseAvailable(this.exercise, this.gradedParticipation);
    }

    /**
     * Resuming an exercise is not possible in the exam, otherwise see exercise.utils -> isResumeExerciseAvailable
     */
    isResumeExerciseAvailable(participation?: StudentParticipation): boolean {
        return !this.examMode && isResumeExerciseAvailable(this.exercise, participation);
    }

    /**
     * Practicing an exercise is not possible in the exam, otherwise see exercise.utils -> isStartPracticeAvailable
     */
    isStartPracticeAvailable(): boolean {
        return !this.examMode && isStartPracticeAvailable(this.exercise, this.practiceParticipation);
    }

    startExercise() {
        this.exercise.loading = true;
        this.courseExerciseService
            .startExercise(this.exercise.id!)
            .pipe(finalize(() => (this.exercise.loading = false)))
            .subscribe({
                next: (participation) => {
                    if (participation) {
                        this.receiveNewParticipation(participation);
                    }
                    if (this.programmingExercise) {
                        if (participation?.initializationState === InitializationState.INITIALIZED) {
                            if (this.programmingExercise.allowOfflineIde) {
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
        this.exercise.loading = true;
        const participation = testRun ? this.practiceParticipation : this.gradedParticipation;
        this.courseExerciseService
            .resumeProgrammingExercise(this.exercise.id!, participation!.id!)
            .pipe(finalize(() => (this.exercise.loading = false)))
            .subscribe({
                next: (resumedParticipation: StudentParticipation) => {
                    if (resumedParticipation) {
                        // Otherwise the client would think that all results are loaded, but there would not be any (=> no graded result).
                        const replacedIndex = this.exercise.studentParticipations!.indexOf(participation!);
                        this.exercise.studentParticipations![replacedIndex] = resumedParticipation;
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
        return !this.exercise.isAtLeastTutor && !!this.exercise.startDate && dayjs().isBefore(this.exercise.startDate);
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
        const shouldPreferPractice = this.participationService.shouldPreferPractice(this.exercise);
        const activePracticeParticipation = this.practiceParticipation?.initializationState === InitializationState.INITIALIZED && (shouldPreferPractice || this.examMode);
        const activeGradedParticipation = this.gradedParticipation?.initializationState === InitializationState.INITIALIZED;
        const inactiveGradedParticipation =
            !!this.gradedParticipation?.initializationState &&
            [InitializationState.INACTIVE, InitializationState.FINISHED].includes(this.gradedParticipation.initializationState) &&
            !isStartExerciseAvailable(this.exercise, this.gradedParticipation);

        return activePracticeParticipation || activeGradedParticipation || inactiveGradedParticipation;
    }

    /**
     * Returns true if the repository uri of the active participation is set
     * We don't want to show buttons that would interact with the repository if the repository is not set
     */
    private isRepositoryUriSet(): boolean {
        const participations = this.exercise.studentParticipations ?? [];
        const activeParticipation: ProgrammingExerciseStudentParticipation = this.participationService.getSpecificStudentParticipation(participations, false) ?? participations[0];
        return !!activeParticipation?.repositoryUri;
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

    get allowEditing(): boolean {
        return (
            (this.gradedParticipation?.initializationState === InitializationState.INITIALIZED && this.beforeDueDate) ||
            this.gradedParticipation?.initializationState === InitializationState.FINISHED
        );
    }
}
