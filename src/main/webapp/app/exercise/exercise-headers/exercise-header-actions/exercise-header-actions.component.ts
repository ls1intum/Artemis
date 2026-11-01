import {
    TumAetUiButtonDirective,
    TumAetUiMenuComponent,
    TumAetUiMenuItemDirective,
    TumAetUiMenuTriggerDirective,
    TumAetUiPopoverComponent,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';

import { ProgrammingExerciseParticipationActionsComponent } from 'app/exercise/participation/programming-exercise-participation-actions/programming-exercise-participation-actions.component';
import { StudentExerciseActionsDirective } from 'app/exercise/participation/student-exercise-actions.directive';
import { Component, ElementRef, HostListener, computed, effect, inject, input, output, signal, viewChild, viewChildren } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import dayjs from 'dayjs/esm';

import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition, faEye, faFileSignature, faListAlt, faPaperPlane, faPlayCircle, faRobot, faSignal, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { ExternalCloningService } from 'app/programming/shared/services/external-cloning.service';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { PlagiarismCaseInfo } from 'app/plagiarism/shared/entities/PlagiarismCaseInfo';
import { ParticipationMode } from 'app/exercise/exercise-headers/participation-mode-toggle/participation-mode-toggle.component';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { NgTemplateOutlet } from '@angular/common';
import { ExerciseActionButtonComponent } from 'app/shared-ui/components/buttons/exercise-action-button/exercise-action-button.component';
import { CodeButtonComponent, type VcsAccessTokenSourceInfo } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import {
    DEFAULT_ATHENA_FEEDBACK_REQUEST_LIMIT,
    RequestFeedbackButtonComponent,
    countSuccessfulAthenaFeedbackRequests,
} from 'app/course/overview/exercise-details/request-feedback-button/request-feedback-button.component';
import { StartPracticeModeButtonComponent } from 'app/course/overview/exercise-details/start-practice-mode-button/start-practice-mode-button.component';
import { AccountService } from 'app/core/auth/account.service';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
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
        TumAetUiMenuComponent,
        TumAetUiMenuItemDirective,
        TumAetUiMenuTriggerDirective,
        TumAetUiPopoverComponent,
        TumAetUiTooltipDirective,
        TumAetUiButtonDirective,
        ProgrammingExerciseParticipationActionsComponent,
        FaIconComponent,
        RouterLink,
        TranslateDirective,
        ArtemisTranslatePipe,
        NgTemplateOutlet,
        ExerciseActionButtonComponent,
        StartPracticeModeButtonComponent,
        CodeButtonComponent,
        RequestFeedbackButtonComponent,
    ],
    providers: [ExternalCloningService],
})
export class ExerciseHeaderActionsComponent extends StudentExerciseActionsDirective {
    private readonly elementRef = inject(ElementRef);
    private readonly quizExerciseService = inject(QuizExerciseService);
    private readonly router = inject(Router);
    private readonly accountService = inject(AccountService);

    private readonly programmingActions = viewChildren(ProgrammingExerciseParticipationActionsComponent);
    private readonly actionButtons = viewChildren(ExerciseActionButtonComponent);
    private readonly submitOrigin = viewChild<ElementRef<HTMLElement>>('submitOrigin');
    private readonly submitPopoverRef = viewChild<TumAetUiPopoverComponent>('submitPopoverRef');

    protected readonly faUsers = faUsers;
    protected readonly faEye = faEye;
    protected readonly faPlayCircle = faPlayCircle;
    protected readonly faWrench = faWrench;
    protected readonly faFileSignature = faFileSignature;
    protected readonly faPaperPlane = faPaperPlane;
    protected readonly faRobot = faRobot;

    protected readonly ExerciseType = ExerciseType;
    protected readonly PlagiarismVerdict = PlagiarismVerdict;

    readonly onSubmitExercise = input<() => void>();
    readonly onContinueExercise = input<() => void>();
    readonly onRestartPractice = input<() => boolean>();
    readonly submitDisabled = input<boolean>(false);
    readonly submitLabel = input<string>('entity.action.submit');
    readonly quizPracticeAttemptFinished = input<boolean>(false);
    readonly plagiarismCaseInfo = input<PlagiarismCaseInfo>();
    readonly participationMode = input<ParticipationMode>('graded');

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
            return this.quizExerciseService.getStatus(exercise);
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

    readonly activeParticipationForCode = computed(() => {
        return this.participationMode() === 'practice' ? (this._practiceParticipation() ?? this._gradedParticipation()) : this._gradedParticipation();
    });
    readonly programmingParticipationForCodeButton = computed(() => this.getProgrammingParticipationForCodeButton());
    readonly repositoryUriForCodeButton = computed(() => this.getRepositoryUriForCodeButton());
    readonly vcsAccessTokenDataForCodeButton = computed(() => this.getVcsAccessTokenDataForCodeButton());

    private readonly feedbackDeadlineClock = signal(Date.now());
    private readonly pendingAthenaFeedbackDeadline = computed(() => {
        const type = this.exercise().type;
        if (type !== ExerciseType.TEXT && type !== ExerciseType.MODELING) {
            return 0;
        }
        return getAllResultsOfAllSubmissions(this.activeParticipationForCode()?.submissions).reduce((deadline, result) => {
            if (result.assessmentType !== AssessmentType.AUTOMATIC_ATHENA || result.successful !== undefined) {
                return deadline;
            }
            return Math.max(deadline, result.completionDate ? dayjs(result.completionDate).valueOf() : Infinity);
        }, 0);
    });
    protected readonly hasPendingAthenaFeedback = computed(() => this.pendingAthenaFeedbackDeadline() > this.feedbackDeadlineClock());

    readonly repositoryViewLink = computed(() => {
        const participation = this.activeParticipationForCode();
        if (!participation?.id) {
            return ['/courses', this.courseId(), 'exercises', this.exercise().id!];
        }
        return ['/courses', this.courseId(), 'exercises', this.exercise().id!, 'repository', participation.id];
    });

    readonly userLLMSelection = computed(() => this.accountService.userIdentity()?.selectedLLMUsage);
    readonly hasUserAcceptedLLM = computed(() => {
        const selection = this.userLLMSelection();
        return selection === LLMSelectionDecision.CLOUD_AI || selection === LLMSelectionDecision.LOCAL_AI;
    });
    readonly showFeedbackPopover = computed(() => {
        const exercise = this.exercise();
        return (
            !this.examMode() &&
            this.hasUserAcceptedLLM() &&
            this.athenaEnabled &&
            (exercise.course?.athenaFormativeFeedbackEnabled ?? false) &&
            (exercise.type === ExerciseType.PROGRAMMING || exercise.type === ExerciseType.TEXT || exercise.type === ExerciseType.MODELING)
        );
    });
    readonly hasProgrammingSubmission = computed(() => !!this.activeParticipationForCode()?.submissions?.some((submission) => submission.submitted));

    constructor() {
        super();
        // A reloaded pending request must unlock at its lease deadline, even without a result notification.
        effect((onCleanup) => {
            const deadline = this.pendingAthenaFeedbackDeadline();
            const now = Date.now();
            this.feedbackDeadlineClock.set(now);
            if (Number.isFinite(deadline) && deadline > now) {
                const timeout = setTimeout(() => this.feedbackDeadlineClock.set(Date.now()), deadline - now + 1);
                onCleanup(() => clearTimeout(timeout));
            }
        });
        // Automatically make the rightmost action button primary, all others secondary
        effect(() => {
            const buttons = [...this.actionButtons(), ...this.programmingActions().flatMap((actions) => actions.actionButtons())];
            const nonOutlined = buttons.filter((b) => !b.outlined());
            nonOutlined.forEach((b) => b.overrideSecondary.set(true));
            if (nonOutlined.length > 0) {
                nonOutlined[nonOutlined.length - 1].overrideSecondary.set(false);
            }
        });
    }

    protected override resetExerciseSpecificState(): void {
        this._uninitializedQuiz.set(false);
        this._quizNotStarted.set(false);
        this._programmingExercise.set(undefined);
        this._editorLabel.set(undefined);
    }

    private getRepositoryUriForCodeButton(): string {
        const participations = this.studentParticipations();
        const participation = this.participationService.getSpecificStudentParticipation(participations, this.participationMode() === 'practice') ?? participations[0];
        return participation && this.isProgrammingExerciseStudentParticipation(participation) ? (participation.repositoryUri ?? '') : '';
    }

    private isProgrammingExerciseStudentParticipation(participation: StudentParticipation): participation is ProgrammingExerciseStudentParticipation {
        return participation.type === ParticipationType.PROGRAMMING;
    }

    private getProgrammingParticipationForCodeButton(): ProgrammingExerciseStudentParticipation | undefined {
        const participations = this.studentParticipations();
        return this.participationService.getSpecificStudentParticipation(participations, this.participationMode() === 'practice') ?? participations[0];
    }

    private getVcsAccessTokenDataForCodeButton(): VcsAccessTokenSourceInfo {
        const participationId = this.programmingParticipationForCodeButton()!.id!;
        return { returnedTokenType: 'participation-token', participationId };
    }

    /**
     * Whether the quiz "Start practice" button should be shown: in the graded view only until the first practice
     * attempt exists, and in the practice view only once the current attempt is finished (to start another). It is
     * hidden while viewing a previous result (where the "Continue" action returns to the latest submission first).
     * Once a practice attempt exists, a student in the graded view switches to practice mode, which opens the latest
     * practice result, and starts another attempt from there.
     */
    readonly showQuizStartPracticeButton = computed(() => {
        if (!this.isStartPracticeAvailable() || this.onContinueExercise()) {
            return false;
        }
        if (this.participationMode() === 'practice') {
            return this.quizPracticeAttemptFinished();
        }
        return !this._practiceParticipation();
    });

    private createTutorActions(): InstructorActionItem[] {
        const tutorActionItems = [...this.getDefaultItems()];
        if (this.exercise().type === ExerciseType.QUIZ) {
            tutorActionItems.push(...this.getQuizItems());
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
            this.getParticipationItem(),
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
        void this.router.navigate(['/courses', this.courseId(), 'exercises', 'quiz-exercises', this.exercise().id, quizMode]);
    }

    closeSubmitPopover() {
        this.submitPopoverRef()?.close();
    }

    submitAndShowPopover() {
        if (this.hasPendingAthenaFeedback()) {
            return;
        }
        this.onSubmitExercise()?.();
        if (!this.hasUserAcceptedLLM() || countSuccessfulAthenaFeedbackRequests(this.activeParticipationForCode()) >= DEFAULT_ATHENA_FEEDBACK_REQUEST_LIMIT) {
            return;
        }
        const origin = this.submitOrigin()?.nativeElement;
        if (origin) this.submitPopoverRef()?.open(origin);
    }

    @HostListener('document:click', ['$event'])
    onDocumentClick(event: MouseEvent) {
        const pop = this.submitPopoverRef();
        if (!pop?.isOpen()) {
            return;
        }

        const target = event.target as HTMLElement;

        // Don't close if clicking inside the popover, the trigger element, or an alert
        if (target.closest('[role="dialog"]') || target.closest('jhi-alert-overlay') || this.elementRef.nativeElement.contains(target)) {
            return;
        }

        pop.close();
    }
}
