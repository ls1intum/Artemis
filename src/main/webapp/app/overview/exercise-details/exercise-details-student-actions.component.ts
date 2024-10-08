import { Component, ContentChild, EventEmitter, HostBinding, Input, OnChanges, OnInit, Output, TemplateRef } from '@angular/core';
import { Router } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { ExternalCloningService } from 'app/exercises/programming/shared/service/external-cloning.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { InitializationState } from 'app/entities/participation/participation.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { hasExerciseDueDatePassed, isResumeExerciseAvailable, isStartExerciseAvailable, isStartPracticeAvailable } from 'app/exercises/shared/exercise/exercise.utils';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ArtemisQuizService } from 'app/shared/quiz/quiz.service';
import { finalize } from 'rxjs/operators';
import { faCodeBranch, faEye, faFolderOpen, faPenSquare, faPlayCircle, faRedo, faUsers } from '@fortawesome/free-solid-svg-icons';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { TranslateService } from '@ngx-translate/core';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import dayjs from 'dayjs/esm';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_ATHENA, PROFILE_LOCALVC } from 'app/app.constants';
import { AssessmentType } from 'app/entities/assessment-type.model';

@Component({
    selector: 'jhi-exercise-details-student-actions',
    templateUrl: './exercise-details-student-actions.component.html',
    styleUrls: ['../course-overview.scss'],
    providers: [ExternalCloningService],
})
export class ExerciseDetailsStudentActionsComponent implements OnInit, OnChanges {
    readonly FeatureToggle = FeatureToggle;
    readonly ExerciseType = ExerciseType;
    readonly InitializationState = InitializationState;

    @Input() @HostBinding('class.col') equalColumns = true;
    @Input() @HostBinding('class.col-auto') smallColumns = false;

    @Input() exercise: Exercise;
    @Input() courseId: number;
    @Input() smallButtons: boolean;
    @Input() examMode: boolean;
    @Input() isGeneratingFeedback: boolean;

    @Output() generatingFeedback: EventEmitter<void> = new EventEmitter<void>();

    // extension points, see shared/extension-point
    @ContentChild('overrideCodeAndOnlineEditorButton') overrideCodeAndOnlineEditorButton: TemplateRef<any>;

    uninitializedQuiz: boolean;
    quizNotStarted: boolean;
    gradedParticipation?: StudentParticipation;
    practiceParticipation?: StudentParticipation;
    programmingExercise?: ProgrammingExercise;
    isTeamAvailable: boolean;
    hasRatedGradedResult: boolean;
    beforeDueDate: boolean;
    editorLabel?: string;
    localVCEnabled = false;
    athenaEnabled = false;
    routerLink: string;
    repositoryLink: string;

    // Icons
    readonly faFolderOpen = faFolderOpen;
    readonly faUsers = faUsers;
    readonly faEye = faEye;
    readonly faPlayCircle = faPlayCircle;
    readonly faRedo = faRedo;
    readonly faCodeBranch = faCodeBranch;
    readonly faPenSquare = faPenSquare;

    private feedbackSent = false;

    constructor(
        private alertService: AlertService,
        private courseExerciseService: CourseExerciseService,
        private router: Router,
        private translateService: TranslateService,
        private participationService: ParticipationService,
        private profileService: ProfileService,
    ) {}

    ngOnInit(): void {
        this.repositoryLink = this.router.url;
        if (this.repositoryLink.endsWith('exercises')) {
            this.repositoryLink += `/${this.exercise.id}`;
        }
        if (this.repositoryLink.includes('exams')) {
            this.repositoryLink += `/exercises/${this.exercise.id}`;
        }
        if (this.repositoryLink.includes('dashboard')) {
            const parts = this.repositoryLink.split('/');
            this.repositoryLink = [...parts.slice(0, parts.indexOf('dashboard')), 'exercises', this.exercise.id].join('/');
        }
        if (this.repositoryLink.includes('lectures')) {
            const parts = this.repositoryLink.split('/');
            this.repositoryLink = [...parts.slice(0, parts.indexOf('lectures')), 'exercises', this.exercise.id].join('/');
        }

        if (this.exercise.type === ExerciseType.QUIZ) {
            const quizExercise = this.exercise as QuizExercise;
            this.uninitializedQuiz = ArtemisQuizService.isUninitialized(quizExercise);
            this.quizNotStarted = ArtemisQuizService.notStarted(quizExercise);
        } else if (this.exercise.type === ExerciseType.PROGRAMMING) {
            this.programmingExercise = this.exercise as ProgrammingExercise;
            this.profileService.getProfileInfo().subscribe((profileInfo) => {
                this.localVCEnabled = profileInfo.activeProfiles?.includes(PROFILE_LOCALVC);
                this.athenaEnabled = profileInfo.activeProfiles?.includes(PROFILE_ATHENA);
            });
        } else if (this.exercise.type === ExerciseType.MODELING) {
            this.editorLabel = 'openModelingEditor';
            this.profileService.getProfileInfo().subscribe((profileInfo) => {
                this.athenaEnabled = profileInfo.activeProfiles?.includes(PROFILE_ATHENA);
            });
        } else if (this.exercise.type === ExerciseType.TEXT) {
            this.editorLabel = 'openTextEditor';
            this.profileService.getProfileInfo().subscribe((profileInfo) => {
                this.athenaEnabled = profileInfo.activeProfiles?.includes(PROFILE_ATHENA);
            });
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
        this.practiceParticipation = this.participationService.getSpecificStudentParticipation(studentParticipations, true);

        this.hasRatedGradedResult = !!this.gradedParticipation?.results?.some((result) => result.rated === true && result.assessmentType !== AssessmentType.AUTOMATIC_ATHENA);
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
                error: () => {
                    this.alertService.error('artemisApp.exercise.startError');
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
                        resumedParticipation.results = participation ? participation.results : [];
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

    requestFeedback() {
        if (!this.assureConditionsSatisfied()) return;
        if (this.exercise.type === ExerciseType.PROGRAMMING) {
            const confirmLockRepository = this.translateService.instant('artemisApp.exercise.lockRepositoryWarning');
            if (!window.confirm(confirmLockRepository)) {
                return;
            }
        }

        this.courseExerciseService.requestFeedback(this.exercise.id!).subscribe({
            next: (participation: StudentParticipation) => {
                if (participation) {
                    this.generatingFeedback.emit();
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

    buildPlanUrl(participation: StudentParticipation) {
        return (participation as ProgrammingExerciseStudentParticipation).buildPlanUrl;
    }

    /**
     * Checks if the conditions for requesting automatic non-graded feedback are satisfied.
     * The student can request automatic non-graded feedback under the following conditions:
     * 1. They have a graded submission.
     * 2. The deadline for the exercise has not been exceeded.
     * 3. There is no already pending feedback request.
     * @returns {boolean} `true` if all conditions are satisfied, otherwise `false`.
     */
    assureConditionsSatisfied(): boolean {
        this.updateParticipations();
        if (this.exercise.type === ExerciseType.PROGRAMMING) {
            const latestResult = this.gradedParticipation?.results && this.gradedParticipation.results.find(({ assessmentType }) => assessmentType === AssessmentType.AUTOMATIC);
            const someHiddenTestsPassed = latestResult?.score !== undefined;
            const testsNotPassedWarning = this.translateService.instant('artemisApp.exercise.notEnoughPoints');
            if (!someHiddenTestsPassed) {
                window.alert(testsNotPassedWarning);
                return false;
            }
        }

        const afterDueDate = !this.exercise.dueDate || dayjs().isSameOrAfter(this.exercise.dueDate);
        const dueDateWarning = this.translateService.instant('artemisApp.exercise.feedbackRequestAfterDueDate');
        if (afterDueDate) {
            this.alertService.warning(dueDateWarning);
            return false;
        }

        const requestAlreadySent = (this.gradedParticipation?.individualDueDate && this.gradedParticipation.individualDueDate.isBefore(Date.now())) ?? false;
        const requestAlreadySentWarning = this.translateService.instant('artemisApp.exercise.feedbackRequestAlreadySent');
        if (requestAlreadySent) {
            this.alertService.warning(requestAlreadySentWarning);
            return false;
        }

        if (this.gradedParticipation?.results) {
            const athenaResults = this.gradedParticipation.results.filter((result) => result.assessmentType === 'AUTOMATIC_ATHENA');
            const countOfSuccessfulRequests = athenaResults.length;

            if (countOfSuccessfulRequests >= 10) {
                const rateLimitExceededWarning = this.translateService.instant('artemisApp.exercise.maxAthenaResultsReached');
                this.alertService.warning(rateLimitExceededWarning);
                return false;
            }
        }

        if (this.hasAthenaResultForlatestSubmission()) {
            const submitFirstWarning = this.translateService.instant('artemisApp.exercise.submissionAlreadyHasAthenaResult');
            this.alertService.warning(submitFirstWarning);
            return false;
        }
        return true;
    }

    hasAthenaResultForlatestSubmission(): boolean {
        if (this.gradedParticipation?.submissions && this.gradedParticipation?.results) {
            const sortedSubmissions = this.gradedParticipation.submissions.slice().sort((a, b) => {
                const dateA = this.getDateValue(a.submissionDate) ?? -Infinity;
                const dateB = this.getDateValue(b.submissionDate) ?? -Infinity;
                return dateB - dateA;
            });

            return this.gradedParticipation.results.some((result) => result.submission?.id === sortedSubmissions[0]?.id);
        }
        return false;
    }

    private getDateValue = (date: any): number => {
        if (dayjs.isDayjs(date)) {
            return date.valueOf();
        }
        if (date instanceof Date) {
            return date.valueOf();
        }
        if (typeof date === 'string') {
            return new Date(date).valueOf();
        }
        return -Infinity; // fallback for null, undefined, or invalid dates
    };
}
