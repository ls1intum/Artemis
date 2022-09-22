import { Component, ContentChild, HostBinding, Input, TemplateRef } from '@angular/core';
import { Router } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { HttpClient } from '@angular/common/http';
import { SourceTreeService } from 'app/exercises/programming/shared/service/sourceTree.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { InitializationState, Participation } from 'app/entities/participation/participation.model';
import { Exercise, ExerciseType, ParticipationStatus } from 'app/entities/exercise.model';
import { isStartExerciseAvailable, isStartPracticeAvailable, participationStatus } from 'app/exercises/shared/exercise/exercise.utils';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { finalize } from 'rxjs/operators';
import { faExternalLinkAlt, faEye, faFolderOpen, faPlayCircle, faRedo, faSignal, faUsers } from '@fortawesome/free-solid-svg-icons';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import dayjs from 'dayjs/esm';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';

@Component({
    selector: 'jhi-exercise-details-student-actions',
    templateUrl: './exercise-details-student-actions.component.html',
    styleUrls: ['../course-overview.scss'],
    providers: [SourceTreeService],
})
export class ExerciseDetailsStudentActionsComponent {
    readonly FeatureToggle = FeatureToggle;
    readonly ExerciseType = ExerciseType;
    readonly ParticipationStatus = ParticipationStatus;

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

    // Icons
    faFolderOpen = faFolderOpen;
    faUsers = faUsers;
    faEye = faEye;
    faPlayCircle = faPlayCircle;
    faSignal = faSignal;
    faRedo = faRedo;
    faExternalLinkAlt = faExternalLinkAlt;

    constructor(
        private alertService: AlertService,
        private courseExerciseService: CourseExerciseService,
        private httpClient: HttpClient,
        private router: Router,
        private participationService: ParticipationService,
    ) {}

    /**
     * check if practiceMode is available
     * @return {boolean}
     */
    public isPracticeModeAvailable(): boolean {
        if (!this.examMode && !!this.exercise) {
            switch (this.exercise.type) {
                case ExerciseType.QUIZ:
                    const quizExercise = this.exercise as QuizExercise;
                    return quizExercise.isOpenForPractice! && quizExercise.quizEnded!;
                case ExerciseType.PROGRAMMING:
                    const programmingExercise: ProgrammingExercise = this.exercise as ProgrammingExercise;
                    return dayjs().isAfter(programmingExercise.dueDate) && !programmingExercise.teamMode;
                default:
                    return false;
            }
        } else {
            return false;
        }
    }

    /**
     * see exercise.utils -> isStartExerciseAvailable
     */
    isStartExerciseAvailable(): boolean {
        return isStartExerciseAvailable(this.exercise as ProgrammingExercise);
    }

    /**
     * see exercise.utils -> isStartExerciseAvailable
     */
    isStartPracticeAvailable(): boolean {
        return isStartPracticeAvailable(this.exercise as ProgrammingExercise);
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
                        this.exercise.participationStatus = this.participationStatusWrapper();
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

    startPractice(): void {
        this.exercise.loading = true;
        this.courseExerciseService
            .startPractice(this.exercise.id!)
            .pipe(finalize(() => (this.exercise.loading = false)))
            .subscribe({
                next: (participation) => {
                    if (participation) {
                        const gradedParticipation = this.participationService.getSpecificStudentParticipation(this.exercise.studentParticipations ?? [], false);
                        if (gradedParticipation) {
                            this.exercise.studentParticipations = [gradedParticipation, participation];
                        } else {
                            this.exercise.studentParticipations = [participation];
                        }
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
                        this.exercise.participationStatus = participationStatus(this.exercise);
                        this.alertService.success('artemisApp.exercise.resumeProgrammingExercise');
                    }
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
    participationStatusWrapper(testRun?: boolean): ParticipationStatus {
        return participationStatus(this.exercise, testRun);
    }

    /**
     * Display the 'open code editor' or 'clone repo' buttons if
     * - the participation is initialized (build plan exists, no clean up happened), or
     * - the participation is inactive (build plan cleaned up), but can not be resumed (e.g. because we're after the due date)
     */
    public shouldDisplayIDEButtons(): boolean {
        return !!this.exercise.studentParticipations?.some((participation) => {
            const status = participationStatus(this.exercise, participation.testRun);
            return status === ParticipationStatus.INITIALIZED || (status === ParticipationStatus.INACTIVE && !isStartExerciseAvailable(this.exercise) && !participation.testRun);
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

    codeEditorButtonLable(participation: StudentParticipation) {
        if ((this.exercise.studentParticipations?.length ?? 0) > 1 || participation.testRun) {
            return 'artemisApp.exerciseActions.openCodeEditor' + (participation.testRun ? 'Practice' : 'Graded');
        } else {
            return 'artemisApp.exerciseActions.openCodeEditor';
        }
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
