import { AfterViewInit, Component, ContentChild, EventEmitter, HostBinding, Input, Output, TemplateRef } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
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
import { ExerciseOperationMode } from 'app/ExerciseOperationMode';

@Component({
    selector: 'jhi-exercise-details-student-actions',
    templateUrl: './exercise-details-student-actions.component.html',
    styleUrls: ['../course-overview.scss'],
    providers: [SourceTreeService],
})
export class ExerciseDetailsStudentActionsComponent implements AfterViewInit {
    // <editor-fold desc="Non-Input/Output Attributes">
    // <editor-fold desc="Loaded Attributes (constant)">
    // <editor-fold desc="Namespaces">
    public readonly dayjs = dayjs;
    // </editor-fold>

    // <editor-fold desc="Enums">
    public readonly ExerciseType = ExerciseType;
    public readonly ParticipationStatus = ParticipationStatus;
    // TODO: Purpose of FeatureToggle ?
    public readonly FeatureToggle = FeatureToggle;
    // </editor-fold>

    // <editor-fold desc="Icons">
    public readonly faFolderOpen = faFolderOpen;
    public readonly faUsers = faUsers;
    public readonly faEye = faEye;
    public readonly faPlayCircle = faPlayCircle;
    public readonly faSignal = faSignal;
    public readonly faRedo = faRedo;
    public readonly faExternalLinkAlt = faExternalLinkAlt;
    // </editor-fold>
    // </editor-fold>
    // </editor-fold>

    // <editor-fold desc="Input Attributes">
    // <editor-fold desc="Constant Inputs">
    // TODO: Purpose of equalColumns and smallColumns ?
    @Input() @HostBinding('class.col') equalColumns = true;
    @Input() @HostBinding('class.col-auto') smallColumns = false;
    @Input() readonly courseId: number;
    @Input() readonly exercise: Exercise;
    @Input() readonly actionsOnly: boolean;
    @Input() readonly smallButtons: boolean;
    @Input() readonly showResult: boolean;
    // </editor-fold>

    // <editor-fold desc="Variable Inputs">
    @Input() exerciseOperationMode: ExerciseOperationMode;
    // </editor-fold>
    // </editor-fold>

    // <editor-fold desc="Output Attributes">
    @Output() readonly onTogglePracticeMode: EventEmitter<boolean> = new EventEmitter();
    // </editor-fold>

    // extension points, see shared/extension-point
    // TODO: Purpose of overrideCloneOnlineEditorButton ?
    @ContentChild('overrideCloneOnlineEditorButton') readonly overrideCloneOnlineEditorButton: TemplateRef<any>;

    // <editor-fold desc="Constructor">
    constructor(
        private alertService: AlertService,
        private courseExerciseService: CourseExerciseService,
        private httpClient: HttpClient,
        private router: Router,
        private activatedRoute: ActivatedRoute,
    ) {}
    // </editor-fold>

    // <editor-fold desc="Lifetime related methods">
    public ngAfterViewInit(): void {
        // console.log(this);
    }
    // </editor-fold>

    // <editor-fold desc="Exercise related methods">
    // <editor-fold desc="General methods">
    public isInExamMode(): boolean {
        switch (this.exerciseOperationMode) {
            case ExerciseOperationMode.EXAM:
                return true;
            default:
                return false;
        }
    }
    /**
     * check if practiceMode is available
     * @return {boolean}
     */
    public isPracticeModeAvailable(): boolean {
        if (!this.isInExamMode() && !!this.exercise) {
            switch (this.exercise.type) {
                case ExerciseType.QUIZ:
                    const quizExercise: QuizExercise = this.exercise as QuizExercise;
                    return quizExercise.isOpenForPractice! && quizExercise.quizEnded!;
                case ExerciseType.PROGRAMMING:
                    const programmingExercise: ProgrammingExercise = this.exercise as ProgrammingExercise;
                    return dayjs().isAfter(dayjs(programmingExercise.dueDate));
                default:
                    return false;
            }
        } else {
            return false;
        }
    }

    public isInPracticeMode(): boolean {
        switch (this.exerciseOperationMode) {
            case ExerciseOperationMode.PRACTICE:
                return true;
            default:
                return false;
        }
    }

    public togglePracticeMode(toggle: boolean): void {
        if (this.isPracticeModeAvailable()) {
            this.onTogglePracticeMode.emit(toggle);
        }
    }

    /**
     * Wrapper for using participationStatus() in the template
     *
     * @return {ParticipationStatus}
     */
    public participationStatusWrapper(): ParticipationStatus {
        return participationStatus(this.exercise);
    }
    // </editor-fold>

    // <editor-fold desc="Team related methods">
    public canViewTeam(): boolean {
        return this.participationStatusWrapper() !== ParticipationStatus.NO_TEAM_ASSIGNED && !!this.exercise.teamMode;
    }

    public viewTeam(): Promise<boolean> {
        const participations: StudentParticipation[] | undefined = this.exercise.studentParticipations;
        const assignedTeamId: number | undefined = participations?.length ? participations[0].team?.id : this.exercise.studentAssignedTeamId;
        return this.router.navigate(['/courses', this.courseId, 'exercises', this.exercise.id, 'teams', assignedTeamId]);
    }
    // </editor-fold>

    // <editor-fold desc="Exercise related methods">
    public getStudentParticipationExercise(): StudentParticipation | null {
        if (!!this.exercise.studentParticipations && this.exercise.studentParticipations.length > 0) {
            return this.exercise.studentParticipations[0];
        } else {
            return null;
        }
    }

    public canStartExercise(): boolean {
        switch (this.exercise.type) {
            case ExerciseType.PROGRAMMING:
                return this.participationStatusWrapper() === ParticipationStatus.UNINITIALIZED && isStartExerciseAvailable(this.exercise as ProgrammingExercise);
            default:
                return this.participationStatusWrapper() === ParticipationStatus.UNINITIALIZED;
        }
    }

    public startExercise(): void {
        this.exercise.loading = true;
        this.courseExerciseService
            .startExercise(this.exercise.id!)
            .pipe(finalize(() => (this.exercise.loading = false)))
            .subscribe({
                next: (participation) => {
                    if (participation) {
                        this.exercise.studentParticipations = [participation];
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

    public canOpenExercise(): boolean {
        const studentParticipationExercise: StudentParticipation | null = this.getStudentParticipationExercise();
        if (studentParticipationExercise !== null) {
            return studentParticipationExercise.initializationState === 'INITIALIZED';
        } else {
            return false;
        }
    }

    public canViewSubmissions(): boolean {
        const studentParticipationExercise: StudentParticipation | null = this.getStudentParticipationExercise();
        if (studentParticipationExercise !== null) {
            return (
                studentParticipationExercise.initializationState === 'FINISHED' &&
                (!studentParticipationExercise.results || studentParticipationExercise.results.length === 0 || !this.showResult)
            );
        } else {
            return false;
        }
    }

    public canViewResults(): boolean {
        const studentParticipationExercise: StudentParticipation | null = this.getStudentParticipationExercise();
        if (studentParticipationExercise !== null) {
            return (
                studentParticipationExercise.initializationState === 'FINISHED' &&
                !!studentParticipationExercise.results &&
                studentParticipationExercise.results.length > 0 &&
                this.showResult
            );
        } else {
            return false;
        }
    }

    public proceedExercise(): Promise<boolean> {
        let exerciseType: String;
        switch (this.exercise.type) {
            case ExerciseType.QUIZ:
                exerciseType = 'quiz-exercises';
                return this.router.navigate(['/courses', this.courseId, exerciseType, this.exercise.id, 'live']);
            case ExerciseType.MODELING:
                exerciseType = 'modeling-exercises';
                break;
            case ExerciseType.TEXT:
                exerciseType = 'text-exercises';
                break;
            case ExerciseType.FILE_UPLOAD:
                exerciseType = 'file-upload-exercises';
                break;
            default:
                throw new Error(`proceedExercise is not supported yet for '${this.exercise.type}'!`);
        }
        // @ts-ignore
        return this.router.navigate(['/courses', this.courseId, exerciseType, this.exercise.id, 'participate', this.exercise.studentParticipations[0].id]);
    }

    public showStatistics(): Promise<boolean> {
        return this.router.navigate(['/course-management', this.courseId, 'quiz-exercises', this.exercise.id, 'quiz-point-statistic']);
    }
    // </editor-fold>

    // <editor-fold desc="Practice related methods">
    public getStudentParticipationPractice(): StudentParticipation | null {
        if (!!this.exercise.studentParticipations && this.exercise.studentParticipations.length > 0) {
            return this.exercise.studentParticipations[1];
        } else {
            return null;
        }
    }

    public canStartPractice(): boolean {
        switch (this.exercise.type) {
            case ExerciseType.PROGRAMMING:
                return this.participationStatusWrapper() === ParticipationStatus.UNINITIALIZED && isStartPracticeAvailable(this.exercise as ProgrammingExercise);
            default:
                return this.participationStatusWrapper() === ParticipationStatus.UNINITIALIZED;
        }
    }

    public startPractice(): void {
        this.exercise.loading = true;
        this.courseExerciseService
            .startPractice(this.exercise.id!)
            .pipe(finalize(() => (this.exercise.loading = false)))
            .subscribe({
                next: (participation) => {
                    if (participation) {
                        this.exercise.studentParticipations = [participation];
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

    public canOpenPractice(): boolean {
        const studentParticipationPractice: StudentParticipation | null = this.getStudentParticipationPractice();
        if (studentParticipationPractice !== null) {
            return studentParticipationPractice.initializationState === 'INITIALIZED';
        } else {
            return false;
        }
    }

    public canViewPracticeResults(): boolean {
        const studentParticipationPractice: StudentParticipation | null = this.getStudentParticipationPractice();
        if (studentParticipationPractice !== null) {
            return (
                studentParticipationPractice.initializationState === 'FINISHED' &&
                !!studentParticipationPractice.results &&
                studentParticipationPractice.results.length > 0 &&
                this.showResult
            );
        } else {
            return false;
        }
    }

    public proceedPractice(): Promise<boolean> {
        let exerciseType: String;
        switch (this.exercise.type) {
            case ExerciseType.QUIZ:
                exerciseType = 'quiz-exercises';
                return this.router.navigate(['/courses', this.courseId, exerciseType, this.exercise.id, 'practice']);
            default:
                throw new Error(`proceedPractice is not supported yet for '${this.exercise.type}'!`);
        }
    }
    // </editor-fold>
    // </editor-fold>

    // <editor-fold desc="Programming related methods">

    /**
     * resume the programming exercise
     */
    public resumeProgrammingExercise(): void {
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

    /**
     * Display the 'open code editor' or 'clone repo' buttons if
     * - the participation is initialized (build plan exists, no clean up happened), or
     * - the participation is inactive (build plan cleaned up), but can not be resumed (e.g. because we're after the due date)
     */
    public shouldDisplayIDEButtons(): boolean {
        const status = participationStatus(this.exercise);
        return (
            (status === ParticipationStatus.INITIALIZED || (status === ParticipationStatus.INACTIVE && !isStartExerciseAvailable(this.exercise))) &&
            !!this.exercise.studentParticipations &&
            this.exercise.studentParticipations!.length > 0
        );
    }

    /**
     * check if onlineEditor is allowed
     * @return {boolean}
     */
    public isOnlineEditorAllowed(): boolean {
        // noinspection PointlessBooleanExpressionJS
        return !!(this.exercise as ProgrammingExercise).allowOnlineEditor && this.exerciseOperationMode !== ExerciseOperationMode.EXAM;
    }

    public openOnlineEditor(): Promise<boolean> {
        return this.router.navigate(['/courses', this.courseId, 'programming-exercises', this.exercise.id, 'code-editor', this.exercise.studentParticipations![0].id]);
    }

    /**
     * check if offline IDE is allowed
     * @return {boolean}
     */
    public isOfflineIdeAllowed(): boolean {
        // noinspection PointlessBooleanExpressionJS
        return !!(this.exercise as ProgrammingExercise).allowOfflineIde;
    }

    public repositoryUrl(participation: Participation): string | undefined {
        const programmingParticipation = participation as ProgrammingExerciseStudentParticipation;
        return programmingParticipation.repositoryUrl;
    }

    public publishBuildPlanUrl(): boolean | undefined {
        return (this.exercise as ProgrammingExercise).publishBuildPlanUrl;
    }

    public buildPlanUrl(participation: StudentParticipation): string | undefined {
        return (participation as ProgrammingExerciseStudentParticipation).buildPlanUrl;
    }

    public buildPlanActive(): boolean {
        return !!this.exercise?.studentParticipations?.length && this.exercise.studentParticipations[0].initializationState !== InitializationState.INACTIVE;
    }

    // </editor-fold>
}
