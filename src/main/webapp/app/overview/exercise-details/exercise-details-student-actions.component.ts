import { Component, ContentChild, HostBinding, Input, TemplateRef } from '@angular/core';
import { Router } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { HttpClient } from '@angular/common/http';
import { SourceTreeService } from 'app/exercises/programming/shared/service/sourceTree.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { InitializationState, Participation } from 'app/entities/participation/participation.model';
import { Exercise, ExerciseType, ParticipationStatus } from 'app/entities/exercise.model';
import { isStartExerciseAvailable, participationStatus } from 'app/exercises/shared/exercise/exercise.utils';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { finalize } from 'rxjs/operators';
import { faEye, faFolderOpen, faPlayCircle, faRedo, faSignal, faUsers, faExternalLinkAlt, faComment } from '@fortawesome/free-solid-svg-icons';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';

@Component({
    selector: 'jhi-exercise-details-student-actions',
    templateUrl: './exercise-details-student-actions.component.html',
    styleUrls: ['../course-overview.scss'],
    providers: [SourceTreeService],
})
export class ExerciseDetailsStudentActionsComponent {
    FeatureToggle = FeatureToggle;
    readonly ExerciseType = ExerciseType;
    readonly ParticipationStatus = ParticipationStatus;

    @Input() @HostBinding('class.col') equalColumns = true;
    @Input() @HostBinding('class.col-auto') smallColumns = false;

    @Input() exercise: Exercise;
    @Input() studentParticipation?: StudentParticipation;
    @Input() courseId: number;

    @Input() actionsOnly: boolean;
    @Input() smallButtons: boolean;
    @Input() showResult: boolean;

    @Input() examMode: boolean;

    // extension points, see shared/extension-point
    @ContentChild('overrideCloneOnlineEditorButton') overrideCloneOnlineEditorButton: TemplateRef<any>;

    // Icons
    faComment = faComment;
    faFolderOpen = faFolderOpen;
    faUsers = faUsers;
    faEye = faEye;
    faPlayCircle = faPlayCircle;
    faSignal = faSignal;
    faRedo = faRedo;
    faExternalLinkAlt = faExternalLinkAlt;

    constructor(private alertService: AlertService, private courseExerciseService: CourseExerciseService, private httpClient: HttpClient, private router: Router) {}

    /**
     * check if practiceMode is available
     * @return {boolean}
     */
    isPracticeModeAvailable(): boolean {
        const quizExercise = this.exercise as QuizExercise;
        return quizExercise.isOpenForPractice! && quizExercise.quizEnded!;
    }

    /**
     * see exercise.utils -> isStartExerciseAvailable
     */
    isStartExerciseAvailable(): boolean {
        return isStartExerciseAvailable(this.exercise as ProgrammingExercise);
    }

    /**
     * check if onlineEditor is allowed
     * @return {boolean}
     */
    isOnlineEditorAllowed() {
        return (this.exercise as ProgrammingExercise).allowOnlineEditor;
    }

    /**
     * check if offline IDE is allowed
     * @return {boolean}
     */
    isOfflineIdeAllowed() {
        return (this.exercise as ProgrammingExercise).allowOfflineIde;
    }

    /**
     * start the exercise
     */
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
                        this.exercise.studentParticipations = [participation];
                        this.exercise.participationStatus = participationStatus(this.exercise);
                    }
                    if (this.exercise.type === ExerciseType.PROGRAMMING) {
                        if ((this.exercise as ProgrammingExercise).allowOfflineIde) {
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
    resumeProgrammingExercise() {
        this.exercise.loading = true;
        this.courseExerciseService
            .resumeProgrammingExercise(this.exercise.id!)
            .pipe(finalize(() => (this.exercise.loading = false)))
            .subscribe({
                next: (participation: StudentParticipation) => {
                    if (participation) {
                        // Otherwise the client would think that all results are loaded, but there would not be any (=> no graded result).
                        participation.results = this.exercise.studentParticipations![0] ? this.exercise.studentParticipations![0].results : [];
                        this.exercise.studentParticipations = [participation];
                        this.exercise.participationStatus = participationStatus(this.exercise);
                        this.alertService.success('artemisApp.exercise.resumeProgrammingExercise');
                    }
                },
                error: (error) => {
                    this.alertService.error(`artemisApp.${error.error.entityName}.errors.${error.error.errorKey}`);
                },
            });
    }

    isManualFeedbackRequestsAllowed(): boolean {
        return this.exercise.allowManualFeedbackRequests;
    }

    isFeedbackRequestButtonDisabled(): boolean {
        const participation = this.studentParticipation ?? (this.exercise.studentParticipations && this.exercise.studentParticipations.first());

        const showUngradedResults = true;
        const latestResult = participation?.results && participation.results.find(({ rated }) => showUngradedResults || rated === true);
        const allHiddenTestsPassed = latestResult?.score !== undefined && latestResult.score >= 100;

        const requestAlreadySent = (participation?.individualDueDate && participation.individualDueDate.isBefore(Date.now())) ?? false;

        return !allHiddenTestsPassed || requestAlreadySent;
    }

    requestFeedback() {
        this.exercise.loading = true;
        this.courseExerciseService
            .requestFeedback(this.exercise.id!)
            .pipe(finalize(() => (this.exercise.loading = false)))
            .subscribe({
                next: (participation: StudentParticipation) => {
                    if (participation) {
                        this.alertService.success('artemisApp.exercise.feedbackRequestSent');
                    }
                    // TODO: message when feedback has already been sent
                    //       this.alertService.error('artemisApp.exercise.feedbackRequestAlreadySent');
                },
                error: (error) => {
                    this.alertService.error(`artemisApp.${error.error.entityName}.errors.${error.error.errorKey}`);
                },
            });
    }

    /**
     * Wrapper for using participationStatus() in the template
     *
     * @return {ParticipationStatus}
     */
    participationStatusWrapper(): ParticipationStatus {
        return participationStatus(this.exercise);
    }

    /**
     * Display the 'open code editor' or 'clone repo' buttons if
     * - the participation is initialized (build plan exists, no clean up happened), or
     * - the participation is inactive (build plan cleaned up), but can not be resumed (e.g. because we're after the due date)
     */
    shouldDisplayIDEButtons(): boolean {
        const status = participationStatus(this.exercise);
        return (
            (status === ParticipationStatus.INITIALIZED || (status === ParticipationStatus.INACTIVE && !isStartExerciseAvailable(this.exercise))) &&
            !!this.exercise.studentParticipations &&
            this.exercise.studentParticipations!.length > 0
        );
    }

    /**
     * Returns the id of the team that the student is assigned to (only applicable to team-based exercises)
     *
     * @return {assignedTeamId}
     */
    get assignedTeamId(): number | undefined {
        const participations = this.exercise.studentParticipations;
        return participations && participations.length > 0 ? participations[0].team?.id : this.exercise.studentAssignedTeamId;
    }

    repositoryUrl(participation: Participation) {
        const programmingParticipation = participation as ProgrammingExerciseStudentParticipation;
        return programmingParticipation.repositoryUrl;
    }

    publishBuildPlanUrl() {
        return (this.exercise as ProgrammingExercise).publishBuildPlanUrl;
    }

    buildPlanUrl(participation: StudentParticipation) {
        return (participation as ProgrammingExerciseStudentParticipation).buildPlanUrl;
    }

    buildPlanActive() {
        return (
            !!this.exercise &&
            this.exercise.studentParticipations &&
            this.exercise.studentParticipations.length > 0 &&
            this.exercise.studentParticipations[0].initializationState !== InitializationState.INACTIVE
        );
    }
}
