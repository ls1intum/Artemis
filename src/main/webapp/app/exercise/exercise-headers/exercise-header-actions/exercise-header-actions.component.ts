import { Component, ElementRef, HostListener, computed, effect, inject, input, output, signal, untracked, viewChild, viewChildren } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    IconDefinition,
    faEye,
    faFileSignature,
    faFolderOpen,
    faListAlt,
    faPaperPlane,
    faPlayCircle,
    faRobot,
    faSignal,
    faTable,
    faUsers,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbPopover, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslatePipe } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { QuizExercise, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ExternalCloningService } from 'app/programming/shared/services/external-cloning.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { hasExerciseDueDatePassed, isResumeExerciseAvailable, isStartExerciseAvailable, isStartPracticeAvailable } from 'app/exercise/util/exercise.utils';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { finalize } from 'rxjs/operators';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import dayjs from 'dayjs/esm';
import { PROFILE_ATHENA } from 'app/app.constants';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { PlagiarismCaseInfo } from 'app/plagiarism/shared/entities/PlagiarismCaseInfo';
import { ParticipationMode } from 'app/exercise/exercise-headers/participation-mode-toggle/participation-mode-toggle.component';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { ButtonType } from 'app/shared/components/buttons/button/button.component';
import { NgTemplateOutlet } from '@angular/common';
import { ExerciseActionButtonComponent } from 'app/shared/components/buttons/exercise-action-button/exercise-action-button.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { CodeButtonComponent } from 'app/shared/components/buttons/code-button/code-button.component';
import {
    ATHENA_FEEDBACK_REQUEST_LIMIT,
    RequestFeedbackButtonComponent,
    countSuccessfulAthenaFeedbackRequests,
} from 'app/core/course/overview/exercise-details/request-feedback-button/request-feedback-button.component';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { ExerciseDetailsType, ExerciseService } from 'app/exercise/services/exercise.service';
import { StartPracticeModeButtonComponent } from 'app/core/course/overview/exercise-details/start-practice-mode-button/start-practice-mode-button.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AccountService } from 'app/core/auth/account.service';
import { LLMSelectionDecision } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';
import { ArtemisQuizService } from 'app/quiz/shared/service/quiz.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';

interface InstructorActionItem {
    routerLink: string;
    icon?: IconDefinition;
    translation: string;
}

@Component({
    selector: 'jhi-exercise-header-actions',
    templateUrl: './exercise-header-actions.component.html',
    imports: [
        FaIconComponent,
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        NgbDropdownItem,
        NgbTooltip,
        RouterLink,
        TranslateDirective,
        ArtemisTranslatePipe,
        NgTemplateOutlet,
        ExerciseActionButtonComponent,
        FeatureToggleDirective,
        StartPracticeModeButtonComponent,
        CodeButtonComponent,
        RequestFeedbackButtonComponent,
        NgbPopover,
        TranslatePipe,
    ],
    providers: [ExternalCloningService],
})
export class ExerciseHeaderActionsComponent {
    private readonly elementRef = inject(ElementRef);
    private readonly actionButtons = viewChildren(ExerciseActionButtonComponent);
    private readonly submitPopoverRef = viewChild<NgbPopover>('submitPopoverRef');

    protected readonly faFolderOpen = faFolderOpen;
    protected readonly faUsers = faUsers;
    protected readonly faEye = faEye;
    protected readonly faPlayCircle = faPlayCircle;
    protected readonly faWrench = faWrench;
    protected readonly faFileSignature = faFileSignature;
    protected readonly faPaperPlane = faPaperPlane;
    protected readonly faRobot = faRobot;

    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ExerciseType = ExerciseType;
    protected readonly InitializationState = InitializationState;
    protected readonly ButtonType = ButtonType;
    protected readonly PlagiarismVerdict = PlagiarismVerdict;

    private readonly quizExerciseService = inject(QuizExerciseService);
    private readonly alertService = inject(AlertService);
    private readonly courseExerciseService = inject(CourseExerciseService);
    private readonly participationService = inject(ParticipationService);
    private readonly exerciseService = inject(ExerciseService);
    private readonly profileService = inject(ProfileService);
    private readonly router = inject(Router);
    private readonly accountService = inject(AccountService);

    readonly exercise = input.required<Exercise>();
    readonly courseId = input.required<number>();
    readonly smallButtons = input<boolean>(false);
    readonly examMode = input<boolean>(false);
    readonly isGeneratingFeedback = input<boolean>(false);
    readonly onSubmitExercise = input<() => void>();
    readonly onContinueExercise = input<() => void>();
    readonly onRestartPractice = input<() => boolean>();
    readonly submitDisabled = input<boolean>(false);
    readonly submitLabel = input<string>('entity.action.submit');
    readonly plagiarismCaseInfo = input<PlagiarismCaseInfo>();
    readonly participationMode = input<ParticipationMode>('graded');

    readonly generatingFeedback = output<void>();
    readonly newParticipation = output<StudentParticipation>();
    readonly participationModeChange = output<ParticipationMode>();

    // Instructor actions
    private readonly QUIZ_ENDED_STATUS: (QuizStatus | undefined)[] = [QuizStatus.OPEN_FOR_PRACTICE];
    private readonly QUIZ_EDITABLE_STATUS: (QuizStatus | undefined)[] = [QuizStatus.VISIBLE, QuizStatus.INVISIBLE];

    private readonly baseResource = computed(() => {
        const exercise = this.exercise();
        return `/course-management/${this.courseId()}/${exercise.type}-exercises/${exercise.id}/`;
    });

    private readonly quizExerciseStatus = computed(() => {
        const exercise = this.exercise();
        if (exercise.type === ExerciseType.QUIZ) {
            return this.quizExerciseService.getStatus(exercise as QuizExercise);
        }
        return undefined;
    });

    readonly instructorActionItems = computed(() => {
        const exercise = this.exercise();
        const items: InstructorActionItem[] = [];
        if (exercise.isAtLeastTutor) {
            items.push(...this.createTutorActions());
        }
        if (exercise.isAtLeastEditor) {
            items.push(...this.createEditorActions());
        }
        if (exercise.isAtLeastInstructor && this.QUIZ_ENDED_STATUS.includes(this.quizExerciseStatus())) {
            items.push(this.getReEvaluateItem());
        }
        return items;
    });

    // Student actions state
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

    readonly athenaEnabled = this.profileService.isProfileActive(PROFILE_ATHENA);

    readonly activeParticipationForCode = computed(() => {
        return this.participationMode() === 'practice' ? (this._practiceParticipation() ?? this._gradedParticipation()) : this._gradedParticipation();
    });

    readonly routerLinkForRepositoryView = computed(() => {
        const participation = this.activeParticipationForCode();
        if (!participation?.id) {
            return ['/courses', this.courseId(), 'exercises', this.exercise().id!];
        }
        return ['/courses', this.courseId(), 'exercises', this.exercise().id!, 'repository', participation.id];
    });

    readonly userLLMSelection = computed(() => this.accountService.userIdentity()?.selectedLLMUsage);
    readonly hasUserAcceptedLLM = computed(() => {
        const selection = this.userLLMSelection();
        return selection === LLMSelectionDecision.CLOUD_AI;
    });
    readonly showFeedbackPopover = computed(() => !this.examMode() && (this.exercise().allowFeedbackRequests ?? false) && this.hasUserAcceptedLLM());

    readonly beforeDueDate = computed(() => {
        const exercise = this.exercise();
        return !exercise.dueDate || !hasExerciseDueDatePassed(exercise, this._gradedParticipation());
    });

    constructor() {
        effect(() => {
            const exercise = this.exercise();
            untracked(() => {
                if (!exercise) {
                    return;
                }
                this._studentParticipations.set(exercise.studentParticipations ?? []);
                this.updateParticipations();
                this._isTeamAvailable.set(!!(exercise.teamMode && exercise.studentAssignedTeamIdComputed && exercise.studentAssignedTeamId));
                this.resetExerciseSpecificState();
                this.initializeExerciseSpecificState(exercise);
            });
        });

        // Automatically make the rightmost action button primary, all others secondary
        effect(() => {
            const buttons = this.actionButtons();
            const nonOutlined = buttons.filter((b) => !b.outlined());
            nonOutlined.forEach((b) => b.overrideSecondary.set(true));
            if (nonOutlined.length > 0) {
                nonOutlined[nonOutlined.length - 1].overrideSecondary.set(false);
            }
        });
    }

    private resetExerciseSpecificState(): void {
        this._uninitializedQuiz.set(false);
        this._quizNotStarted.set(false);
        this._programmingExercise.set(undefined);
        this._editorLabel.set(undefined);
    }

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

    isStartExerciseAvailable(): boolean {
        const exercise = this.exercise();
        const individualExerciseOrTeamAssigned = !!(!exercise.teamMode || exercise.studentAssignedTeamId);
        return !this.examMode() && individualExerciseOrTeamAssigned && isStartExerciseAvailable(this.exercise(), this._gradedParticipation());
    }

    isResumeExerciseAvailable(participation?: StudentParticipation): boolean {
        return !this.examMode() && isResumeExerciseAvailable(this.exercise(), participation);
    }

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

    resumeProgrammingExercise(testRun: boolean) {
        this._isLoading.set(true);
        const participation = testRun ? this._practiceParticipation() : this._gradedParticipation();
        this.courseExerciseService
            .resumeProgrammingExercise(this.exercise().id!, participation!.id!)
            .pipe(finalize(() => this._isLoading.set(false)))
            .subscribe({
                next: (resumedParticipation: StudentParticipation) => {
                    if (resumedParticipation) {
                        this.receiveNewParticipation(resumedParticipation);
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

    private isRepositoryUriSet(): boolean {
        const participations = this._studentParticipations();
        const activeParticipation: ProgrammingExerciseStudentParticipation = this.participationService.getSpecificStudentParticipation(participations, false) ?? participations[0];
        return !!activeParticipation?.repositoryUri;
    }

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

    private createTutorActions(): InstructorActionItem[] {
        const tutorActionItems = [...this.getDefaultItems()];
        if (this.exercise().type === ExerciseType.QUIZ) {
            tutorActionItems.push(...this.getQuizItems());
        } else {
            tutorActionItems.push(this.getParticipationItem());
        }
        return tutorActionItems;
    }

    private getDefaultItems(): InstructorActionItem[] {
        return [
            {
                routerLink: this.baseResource(),
                icon: faEye,
                translation: 'entity.action.view',
            },
            {
                routerLink: `${this.baseResource()}scores`,
                icon: faTable,
                translation: 'entity.action.scores',
            },
        ];
    }

    private getQuizItems(): InstructorActionItem[] {
        return [
            {
                routerLink: `${this.baseResource()}preview`,
                icon: faEye,
                translation: 'artemisApp.quizExercise.preview',
            },
            {
                routerLink: `${this.baseResource()}solution`,
                icon: faEye,
                translation: 'artemisApp.quizExercise.solution',
            },
        ];
    }

    private getParticipationItem(): InstructorActionItem {
        return {
            routerLink: `${this.baseResource()}participations`,
            icon: faListAlt,
            translation: 'artemisApp.exercise.participations',
        };
    }

    private createEditorActions(): InstructorActionItem[] {
        const editorItems: InstructorActionItem[] = [];
        const exercise = this.exercise();
        if (exercise.type === ExerciseType.QUIZ) {
            editorItems.push(this.getStatisticItem('quiz-point-statistic'));
            if (this.QUIZ_EDITABLE_STATUS.includes(this.quizExerciseStatus())) {
                editorItems.push(this.getQuizEditItem());
            }
        } else if (exercise.type === ExerciseType.MODELING) {
            editorItems.push(this.getStatisticItem('exercise-statistics'));
        } else if (exercise.type === ExerciseType.PROGRAMMING) {
            editorItems.push(this.getGradingItem());
        }
        return editorItems;
    }

    private getStatisticItem(routerLink: string): InstructorActionItem {
        return {
            routerLink: `${this.baseResource()}${routerLink}`,
            icon: faSignal,
            translation: 'artemisApp.courseOverview.exerciseDetails.instructorActions.statistics',
        };
    }

    private getGradingItem(): InstructorActionItem {
        return {
            routerLink: `${this.baseResource()}grading/test-cases`,
            icon: faFileSignature,
            translation: 'artemisApp.programmingExercise.configureGrading.shortTitle',
        };
    }

    private getQuizEditItem(): InstructorActionItem {
        return {
            routerLink: `${this.baseResource()}edit`,
            icon: faWrench,
            translation: 'entity.action.edit',
        };
    }

    private getReEvaluateItem(): InstructorActionItem {
        return {
            routerLink: `${this.baseResource()}re-evaluate`,
            icon: faWrench,
            translation: 'entity.action.re-evaluate',
        };
    }

    handleQuizAction(quizMode: string): void {
        if (quizMode === 'practice') {
            this.participationModeChange.emit('practice');
            const restartFn = this.onRestartPractice();
            if (restartFn && restartFn()) {
                return;
            }
        }
        this.router.navigate(['/courses', this.courseId(), 'exercises', 'quiz-exercises', this.exercise().id, quizMode]);
    }

    closeSubmitPopover() {
        this.submitPopoverRef()?.close();
    }

    submitAndShowPopover() {
        this.onSubmitExercise()?.();
        const exerciseId = this.exercise().id;
        if (!exerciseId) {
            this.submitPopoverRef()?.open();
            return;
        }
        this.exerciseService.getExerciseDetails(exerciseId).subscribe({
            next: (response: HttpResponse<ExerciseDetailsType>) => {
                const participations = response.body?.exercise.studentParticipations ?? [];
                const practice = this.participationService.getSpecificStudentParticipation(participations, true);
                const graded = this.participationService.getSpecificStudentParticipation(participations, false);
                const participation = practice ?? graded;
                if (countSuccessfulAthenaFeedbackRequests(participation) >= ATHENA_FEEDBACK_REQUEST_LIMIT) {
                    return;
                }
                this.submitPopoverRef()?.open();
            },
            error: () => {
                this.submitPopoverRef()?.open();
            },
        });
    }

    @HostListener('document:click', ['$event'])
    onDocumentClick(event: MouseEvent) {
        const pop = this.submitPopoverRef();
        if (!pop?.isOpen()) {
            return;
        }

        const target = event.target as HTMLElement;

        // Don't close if clicking inside the popover, the trigger element, or an alert
        if (target.closest('.popover') || target.closest('jhi-alert-overlay') || this.elementRef.nativeElement.contains(target)) {
            return;
        }

        pop.close();
    }
}
