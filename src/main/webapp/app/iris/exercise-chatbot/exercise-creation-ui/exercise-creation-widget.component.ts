import {
    faArrowDown,
    faCircle,
    faCircleInfo,
    faCompress,
    faExpand,
    faPaperPlane,
    faRedo,
    faRobot,
    faThumbsDown,
    faThumbsUp,
    faTrash,
    faXmark,
} from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ButtonType } from 'app/shared/components/button.component';
import { Component, ElementRef, Input, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { HttpErrorResponse } from '@angular/common/http';
import {
    ActiveConversationMessageLoadedAction,
    ConversationErrorOccurredAction,
    NumNewMessagesResetAction,
    RateMessageSuccessAction,
    StudentMessageSentAction,
} from 'app/iris/state-store.model';
import {
    IrisArtemisClientMessage,
    IrisMessage,
    IrisSender,
    IrisUserMessage,
    isArtemisClientSentMessage,
    isServerSentMessage,
    isStudentSentMessage,
} from 'app/entities/iris/iris-message.model';
import {
    ExecutionStage,
    ExerciseComponent,
    IrisExercisePlan,
    IrisExercisePlanStep,
    IrisMessageContent,
    IrisMessageContentType,
    IrisTextMessageContent,
    getTextContent,
    hideOrUnhide,
    isComplete,
    isExercisePlan,
    isFailed,
    isHidden,
    isInProgress,
    isNotExecuted,
    isTextContent,
} from 'app/entities/iris/iris-content-type.model';
import { Subscription } from 'rxjs';
import { SharedService } from 'app/iris/shared.service';
import { IrisSessionService } from 'app/iris/session.service';
import { IrisErrorMessageKey, IrisErrorType } from 'app/entities/iris/iris-errors.model';
import dayjs from 'dayjs/esm';
import { AnimationEvent } from '@angular/animations';
import { UserService } from 'app/core/user/user.service';
import { IrisLogoSize } from '../../iris-logo/iris-logo.component';
import { IrisChatSessionService } from 'app/iris/chat-session.service';
import { TranslateService } from '@ngx-translate/core';
import { IrisCodeEditorSessionService } from 'app/iris/code-editor-session.service';
import { IrisExerciseCreationSessionService } from 'app/iris/exercise-creation-session.service';
import { ChatbotService } from 'app/iris/exercise-chatbot/exercise-creation-ui/chatbot.service';

@Component({
    selector: 'jhi-exercise-creation-widget',
    templateUrl: 'exercise-creation-widget.component.html',
    styleUrl: '../widget/chatbot-widget.component.scss',
})
export class ExerciseCreationWidgetComponent implements OnInit, OnDestroy {
    // Icons
    faTrash = faTrash;
    faCircle = faCircle;
    faXmark = faXmark;
    faRedo = faRedo;
    faArrowDown = faArrowDown;
    faCircleInfo = faCircleInfo;
    faCompress = faCompress;
    faExpand = faExpand;
    faPaperPlane = faPaperPlane;
    faRobot = faRobot;
    faThumbsDown = faThumbsDown;
    faThumbsUp = faThumbsUp;

    @Input()
    exerciseId: number;
    @Input()
    stateStore: IrisStateStore;
    @Input()
    courseId: number;
    @Input()
    sessionService: IrisSessionService;
    @Input() paramsOnSend: () => Record<string, unknown> = () => ({});

    // ViewChilds
    @ViewChild('chatBody') chatBody!: ElementRef;
    @ViewChild('scrollArrow') scrollArrow!: ElementRef;
    @ViewChild('messageTextarea', { static: false }) messageTextarea: ElementRef<HTMLTextAreaElement>;
    @ViewChild('unreadMessage', { static: false }) unreadMessage!: ElementRef;

    // State variables
    stateSubscription: Subscription;
    messages: IrisMessage[] = [];
    sessionId: number;
    content: IrisMessageContent;
    newMessageTextContent = '';
    isLoading: boolean;
    numNewMessages = 0;
    error: IrisErrorType | null;
    dots = 1;
    resendAnimationActive = false;
    fadeState = '';
    shouldShowEmptyMessageError = false;
    currentMessageCount: number;
    rateLimit: number;
    rateLimitTimeframeHours: number;
    importExerciseUrl: string;
    chatOpen: boolean;

    // User preferences
    userAccepted: boolean;
    isScrolledToBottom = true;
    rows = 1;
    fullSize = false;
    public ButtonType = ButtonType;
    readonly IrisLogoSize = IrisLogoSize;
    private navigationSubscription: Subscription;
    private readonly MAX_INT_JAVA = 2147483647;

    constructor(
        private userService: UserService,
        private sharedService: SharedService,
        private modalService: NgbModal,
        private translateService: TranslateService,
        private chatbotService: ChatbotService,
    ) {
        this.chatbotService.displayChat$.subscribe(() => {
            console.log('call from widget');
            this.displayChat();
        });
    }

    ngOnInit() {
        this.userService.getIrisAcceptedAt().subscribe((res) => {
            this.userAccepted = !!res;
            if (this.userAccepted) {
                this.loadFirstMessage();
            }
        });

        this.animateDots();

        // Subscribe to state changes
        this.stateSubscription = this.stateStore.getState().subscribe((state) => {
            this.messages = state.messages as IrisMessage[];
            this.isLoading = state.isLoading;
            this.error = state.error;
            this.sessionId = Number(state.sessionId);
            this.numNewMessages = state.numNewMessages;
            if (state.error?.key == IrisErrorMessageKey.EMPTY_MESSAGE) {
                this.shouldShowEmptyMessageError = true;
                this.fadeState = 'start';
            }
            if (this.error) {
                this.getConvertedErrorMap();
            }
            this.currentMessageCount = state.currentMessageCount;
            this.rateLimit = state.rateLimit;
            this.rateLimitTimeframeHours = state.rateLimitTimeframeHours;
        });
    }

    ngOnDestroy() {
        this.stateSubscription.unsubscribe();
    }

    displayChat() {
        // Add logic to display the chat in the widget
        this.chatOpen = true;
        console.log(this.chatOpen);
    }

    /**
     * Inserts the correct link to import the current programming exercise for a new variant generation.
     */
    getFirstMessageContent(): string {
        if (this.isChatSession()) {
            return this.translateService.instant('artemisApp.exerciseChatbot.tutorFirstMessage');
        }
        if (this.isExerciseCreationSession()) {
            return this.translateService.instant('artemisApp.exerciseChatbot.exerciseCreationFirstMessage');
        }
        this.importExerciseUrl = `/course-management/${this.courseId}/programming-exercises/import/${this.exerciseId}`;
        return this.translateService
            .instant('artemisApp.exerciseChatbot.codeEditorFirstMessage')
            .replace(/{link:(.*)}/, '<a href="' + this.importExerciseUrl + '" target="_blank">$1</a>');
    }

    /**
     * Loads the first message in the conversation if it's not already loaded.
     */
    loadFirstMessage(): void {
        const firstMessageContent = {
            type: IrisMessageContentType.TEXT,
            textContent: this.getFirstMessageContent(),
        } as IrisTextMessageContent;

        const firstMessage = {
            sender: IrisSender.ARTEMIS_CLIENT,
            content: [firstMessageContent],
            sentAt: dayjs(),
        } as IrisArtemisClientMessage;

        if (this.messages.length === 0) {
            this.stateStore.dispatch(new ActiveConversationMessageLoadedAction(firstMessage));
        }
    }

    /**
     * Handles the send button click event and sends the user's message.
     */
    onSend(): void {
        if (this.error?.fatal) {
            return;
        }
        if (this.newMessageTextContent.trim() === '') {
            this.stateStore.dispatchAndThen(new ConversationErrorOccurredAction(IrisErrorMessageKey.EMPTY_MESSAGE)).catch(() => this.scrollToBottom('smooth'));
            return;
        }
        if (this.newMessageTextContent) {
            console.log('Sending message: ' + this.newMessageTextContent);
            const message = this.newUserMessage(this.newMessageTextContent);
            const timeoutId = setTimeout(() => {
                // will be cleared by the store automatically
                this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.IRIS_SERVER_RESPONSE_TIMEOUT));
                this.scrollToBottom('smooth');
            }, 20000);
            const params = this.paramsOnSend();
            console.log('Sending params: ' + JSON.stringify(params));
            this.stateStore
                .dispatchAndThen(new StudentMessageSentAction(message, timeoutId))
                .then(() => this.sessionService.sendMessage(this.sessionId, message, params))
                .then(() => this.scrollToBottom('smooth'))
                .catch((error) => this.handleIrisError(error));
            this.newMessageTextContent = '';
        }
        this.scrollToBottom('smooth');
        this.resetChatBodyHeight();
    }

    resendMessage(message: IrisUserMessage) {
        this.resendAnimationActive = true;
        message.messageDifferentiator = message.id ?? Math.floor(Math.random() * this.MAX_INT_JAVA);

        const timeoutId = setTimeout(() => {
            // will be cleared by the store automatically
            this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.IRIS_SERVER_RESPONSE_TIMEOUT));
            this.scrollToBottom('smooth');
        }, 2000000);
        this.stateStore
            .dispatchAndThen(new StudentMessageSentAction(message, timeoutId))
            .then(() => {
                if (message.id) {
                    return this.sessionService.resendMessage(this.sessionId, message, this.paramsOnSend());
                } else {
                    return this.sessionService.sendMessage(this.sessionId, message, this.paramsOnSend());
                }
            })
            .then(() => {
                this.scrollToBottom('smooth');
            })
            .catch((error) => this.handleIrisError(error))
            .finally(() => {
                this.resendAnimationActive = false;
                this.scrollToBottom('smooth');
            });
    }

    private handleIrisError(error: HttpErrorResponse) {
        if (error.status === 403) {
            this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.IRIS_DISABLED));
        } else if (error.status === 429) {
            const map = new Map<string, any>();
            map.set('hours', this.rateLimitTimeframeHours);
            this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.RATE_LIMIT_EXCEEDED, map));
        } else {
            this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.SEND_MESSAGE_FAILED));
        }
    }

    /**
     * Rates a message as helpful or unhelpful.
     * @param messageId - The ID of the message to rate.
     * @param index - The index of the message in the messages array.
     * @param helpful - A boolean indicating if the message is helpful or not.
     */
    rateMessage(messageId: number, index: number, helpful: boolean) {
        this.sessionService
            .rateMessage(this.sessionId, messageId, helpful)
            .then(() => this.stateStore.dispatch(new RateMessageSuccessAction(index, helpful)))
            .catch(() => {
                this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.RATE_MESSAGE_FAILED));
                this.scrollToBottom('smooth');
            });
    }

    /**
     * Closes the chat widget.
     */
    closeChat() {
        this.stateStore.dispatch(new NumNewMessagesResetAction());
        this.sharedService.changeChatOpenStatus(false);
        this.chatOpen = false;
    }

    /**
     * Animates the dots while loading each Iris message in the chat widget.
     */
    animateDots() {
        setInterval(() => {
            this.dots = this.dots < 3 ? (this.dots += 1) : (this.dots = 1);
        }, 500);
    }

    /**
     * Scrolls to the unread message.
     */
    scrollToUnread() {
        setTimeout(() => {
            const unreadMessageElement: HTMLElement = this.unreadMessage?.nativeElement;
            if (unreadMessageElement) {
                unreadMessageElement.scrollIntoView({ behavior: 'auto' });
            }
        });
    }

    /**
     * Scrolls the chat body to the bottom.
     * @param behavior - The scroll behavior.
     */
    scrollToBottom(behavior: ScrollBehavior) {
        setTimeout(() => {
            const chatBodyElement: HTMLElement = this.chatBody.nativeElement;
            chatBodyElement.scrollTo({
                top: chatBodyElement.scrollHeight,
                behavior: behavior,
            });
        });
    }

    /**
     * Checks if the chat body is scrolled to the bottom.
     */
    checkChatScroll() {
        const chatBody = this.chatBody.nativeElement;
        const scrollHeight = chatBody.scrollHeight;
        const scrollTop = chatBody.scrollTop;
        const clientHeight = chatBody.clientHeight;
        this.isScrolledToBottom = scrollHeight - scrollTop - clientHeight < 50;
    }

    /**
     * Clear session and start a new conversation.
     */
    onClearSession(content: any) {
        this.modalService.open(content).result.then((result: string) => {
            if (result === 'confirm') {
                this.isLoading = false;
                if (this.sessionService instanceof IrisExerciseCreationSessionService) {
                    this.createNewCourseSession();
                } else {
                    this.createNewSession();
                }
            }
        });
    }

    /**
     * Accepts the permission to use the chat widget.
     */
    acceptPermission() {
        this.userService.acceptIris().subscribe(() => {
            this.userAccepted = true;
        });
        this.loadFirstMessage();
    }

    /**
     * Handles the key events in the message textarea.
     * @param event - The keyboard event.
     */
    handleKey(event: KeyboardEvent): void {
        if (event.key === 'Enter') {
            if (!this.deactivateSubmitButton()) {
                if (!event.shiftKey) {
                    event.preventDefault();
                    this.onSend();
                } else {
                    const textArea = event.target as HTMLTextAreaElement;
                    const { selectionStart, selectionEnd } = textArea;
                    const value = textArea.value;
                    textArea.value = value.slice(0, selectionStart) + value.slice(selectionEnd);
                    textArea.selectionStart = textArea.selectionEnd = selectionStart + 1;
                }
            } else {
                event.preventDefault();
            }
        }
    }

    /**
     * Handles the input event in the message textarea.
     */
    onInput() {
        this.adjustTextareaRows();
    }

    /**
     * Handles the paste event in the message textarea.
     */
    onPaste() {
        setTimeout(() => {
            this.adjustTextareaRows();
        }, 0);
    }

    /**
     * Adjusts the height of the message textarea based on its content.
     */
    adjustTextareaRows() {
        const textarea: HTMLTextAreaElement = this.messageTextarea.nativeElement;
        textarea.style.height = 'auto'; // Reset the height to auto
        const lineHeight = parseInt(getComputedStyle(textarea).lineHeight, 10);
        const maxRows = 3;
        const maxHeight = lineHeight * maxRows;

        textarea.style.height = `${Math.min(textarea.scrollHeight, maxHeight)}px`;

        this.adjustChatBodyHeight(Math.min(textarea.scrollHeight, maxHeight) / lineHeight);
    }

    /**
     * Handles the row change event in the message textarea.
     */
    onRowChange() {
        const textarea: HTMLTextAreaElement = this.messageTextarea.nativeElement;
        const newRows = textarea.value.split('\n').length;
        if (newRows != this.rows) {
            if (newRows <= 3) {
                textarea.rows = newRows;
                this.adjustChatBodyHeight(newRows);
                this.rows = newRows;
            }
        }
    }

    /**
     * Adjusts the height of the chat body based on the number of rows in the message textarea.
     * @param newRows - The new number of rows.
     */
    adjustChatBodyHeight(newRows: number) {
        const textarea: HTMLTextAreaElement = this.messageTextarea.nativeElement;
        const chatBody: HTMLElement = this.chatBody.nativeElement;
        const scrollArrow: HTMLElement = this.scrollArrow.nativeElement;
        const lineHeight = parseInt(window.getComputedStyle(textarea).lineHeight);
        const rowHeight = lineHeight * newRows;
        setTimeout(() => {
            scrollArrow.style.bottom = `calc(11% + ${rowHeight}px)`;
        }, 10);
        setTimeout(() => {
            chatBody.style.height = `calc(100% - ${rowHeight}px - 64px)`;
        }, 10);
    }

    /**
     * Resets the height of the chat body.
     */
    resetChatBodyHeight() {
        const chatBody: HTMLElement = this.chatBody.nativeElement;
        const textarea: HTMLTextAreaElement = this.messageTextarea.nativeElement;
        const scrollArrow: HTMLElement = this.scrollArrow.nativeElement;
        textarea.rows = 1;
        textarea.style.height = '';
        scrollArrow.style.bottom = '';
        chatBody.style.height = '';
        this.stateStore.dispatch(new NumNewMessagesResetAction());
    }

    /**
     * Gets the title of the button which toggles the execution of an IrisExercisePlan.
     * This depends on the state of the steps in the plan.
     * Currently executing -> 'Pause'
     * All steps complete -> 'Completed'
     * Next step is failed -> 'Retry'
     * Next step is first step -> 'Execute'
     * Paused somewhere in the middle of the plan -> 'Resume'
     * @param plan - The plan to get the button title for.
     */
    getPlanButtonTitle(plan: IrisExercisePlan): string {
        if (plan.executing) {
            return 'Pause';
        }
        const nextStepIndex = this.getNextStepIndex(plan);
        if (nextStepIndex >= plan.steps.length) {
            return 'Completed';
        }
        const nextStep = plan.steps[nextStepIndex];
        if (isFailed(nextStep)) {
            return 'Retry';
        }
        if (nextStepIndex === 0) {
            return 'Execute';
        }
        return 'Resume';
    }

    /**
     * Pauses the execution of an exercise plan.
     * @param plan - The plan to pause.
     */
    pausePlan(plan: IrisExercisePlan) {
        plan.executing = false;
    }

    /**
     * Returns the index of the next step to execute in an IrisExercisePlan.
     * This is the index of the last step that is complete + 1, or 0 if no steps are complete.
     * Range will always be [0, plan.steps.length].
     * @param plan - The plan to get the next step index for.
     */
    getNextStepIndex(plan: IrisExercisePlan) {
        for (let i = plan.steps.length - 1; i >= 0; i--) {
            const step = plan.steps[i];
            if (isComplete(step)) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * Returns whether this plan has more steps to execute.
     * @param plan - The plan to check.
     */
    canExecute(plan: IrisExercisePlan) {
        return this.getNextStepIndex(plan) < plan.steps.length;
    }

    /**
     * Toggles an IrisExercisePlan to be executing.
     * When an exercise plan is executing, the next step will be executed automatically when the previous step is complete.
     * This will also trigger the immediate execution of the next step if it is not already in progress,
     * and we are not still waiting for the previous step to complete.
     * @param messageId - The id of the message which contains the plan.
     * @param plan - The plan to execute.
     */
    setExecuting(messageId: number, plan: IrisExercisePlan) {
        const nextStepIndex = this.getNextStepIndex(plan);
        if (nextStepIndex >= plan.steps.length) {
            console.error('Tried to execute plan that is already complete.');
            return;
        }
        const step = plan.steps[nextStepIndex];
        if (!step) {
            console.error('Could not find next step to execute.');
            return;
        }
        plan.executing = true;
        if (isInProgress(step)) {
            console.log('Step already in progress, awaiting response.');
            return;
        }
        this.executePlanStep(messageId, step);
    }

    /**
     * Execute the specified step of an exercise plan.
     * This will set the executionStage of the step to IN_PROGRESS and send a request to the server.
     * @param messageId - The id of the message which contains the plan.
     * @param step - The step to execute.
     */
    executePlanStep(messageId: number, step: IrisExercisePlanStep) {
        if (!(this.sessionService instanceof IrisCodeEditorSessionService)) {
            return;
        }
        if (!step.id || !step.plan) {
            console.error('Could not execute plan step, one of the required ids is null: ' + step.id + ' ' + step.plan);
            return;
        }
        step.executionStage = ExecutionStage.IN_PROGRESS;
        this.sessionService
            .executePlanStep(this.sessionId, messageId, step.plan, step.id)
            // .then(() => this.stateStore.dispatch(new ExecutePlanSuccessAction(planId)))
            .catch(() => {
                //this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.EXECUTE_PLAN_FAILED));
                step.executionStage = ExecutionStage.FAILED;
                this.scrollToBottom('smooth');
            });
    }

    /**
     * Notifies the chat widget that a step of an exercise plan has been completed.
     * This method is called by the code editor session service.
     * @param messageId - The id of the message which contains the plan.
     * @param planId - The id of the plan.
     * @param stepId - The id of the step that was completed.
     */
    notifyStepCompleted(messageId: number, planId: number, stepId: number) {
        const [plan, step] = this.findPlanStep(messageId, planId, stepId);
        if (!plan || !step) {
            return;
        }
        step.executionStage = ExecutionStage.COMPLETE;
        if (plan.executing) {
            const nextStepIndex = this.getNextStepIndex(plan);
            if (nextStepIndex < plan.steps.length) {
                // The plan is still executing and there are more steps to execute
                this.executePlanStep(messageId, plan.steps[nextStepIndex]);
            } else {
                plan.executing = false;
            }
        }
    }

    notifyStepFailed(messageId: number, planId: number, stepId: number, errorTranslationKey?: IrisErrorMessageKey, translationParams?: Map<string, any>) {
        const [plan, step] = this.findPlanStep(messageId, planId, stepId);
        if (!plan || !step) {
            return;
        }
        plan.executing = false;
        step.executionStage = ExecutionStage.FAILED;
        if (!errorTranslationKey) {
            this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.TECHNICAL_ERROR_RESPONSE));
        } else {
            this.stateStore.dispatch(new ConversationErrorOccurredAction(errorTranslationKey, translationParams));
        }
    }

    private findPlanStep(messageId: number, planId: number, stepId: number): [IrisExercisePlan?, IrisExercisePlanStep?] {
        const message = this.messages.find((m) => m.id === messageId);
        if (!message) {
            console.error('Could not find message with id ' + messageId);
            return [undefined, undefined];
        }
        const plan = message.content.find((c) => c.id === planId) as IrisExercisePlan;
        if (!plan) {
            console.error('Could not find plan with id ' + planId);
            return [undefined, undefined];
        }
        const step = plan.steps.find((s) => s.id === stepId);
        if (!step) {
            console.error('Could not find exercise step with id ' + stepId);
            return [plan, undefined];
        }
        return [plan, step];
    }

    getStepColor(step: IrisExercisePlanStep) {
        switch (step.executionStage) {
            case ExecutionStage.NOT_EXECUTED:
                return 'var(--iris-chat-widget-background)';
            case ExecutionStage.IN_PROGRESS:
                return '#ffc107';
            case ExecutionStage.COMPLETE:
                return '#28a745';
            case ExecutionStage.FAILED:
                return '#dc3545';
        }
    }

    getStepName(step: IrisExercisePlanStep) {
        switch (step.component) {
            case ExerciseComponent.PROBLEM_STATEMENT:
                return 'Problem Statement';
            case ExerciseComponent.SOLUTION_REPOSITORY:
                return 'Solution Repository';
            case ExerciseComponent.TEMPLATE_REPOSITORY:
                return 'Template Repository';
            case ExerciseComponent.TEST_REPOSITORY:
                return 'Test Repository';
        }
    }

    getStepStatus(step: IrisExercisePlanStep) {
        switch (step.executionStage) {
            case ExecutionStage.NOT_EXECUTED:
                return '';
            case ExecutionStage.IN_PROGRESS:
                return 'Generating changes, please be patient...';
            case ExecutionStage.COMPLETE:
                return 'Changes applied.';
            case ExecutionStage.FAILED:
                return 'Encountered an error.';
        }
    }

    /**
     * Creates a new user message.
     * @param message - The content of the message.
     * @returns A new IrisClientMessage object representing the user message.
     */
    newUserMessage(message: string): IrisUserMessage {
        const content = new IrisTextMessageContent(message);
        return {
            sender: IrisSender.USER,
            content: [content],
        };
    }

    isSendMessageFailedError(): boolean {
        return this.error?.key == IrisErrorMessageKey.SEND_MESSAGE_FAILED || this.error?.key == IrisErrorMessageKey.IRIS_SERVER_RESPONSE_TIMEOUT;
    }

    deactivateSubmitButton(): boolean {
        return !this.userAccepted || this.isLoading || (!!this.error && this.error.fatal);
    }

    isEmptyMessageError(): boolean {
        return !!this.error && this.error.key == IrisErrorMessageKey.EMPTY_MESSAGE;
    }

    onFadeAnimationPhaseEnd(event: AnimationEvent) {
        if (event.fromState === 'void' && event.toState === 'start') {
            this.fadeState = 'end';
        }
        if (event.fromState === 'start' && event.toState === 'end') {
            this.shouldShowEmptyMessageError = false;
        }
    }

    getConvertedErrorMap() {
        if (this.error?.paramsMap) {
            if (typeof this.error.paramsMap === 'object') {
                return this.error.paramsMap;
            } else {
                return Object.fromEntries(this.error.paramsMap);
            }
        }
        return null;
    }

    isClearChatButtonEnabled(): boolean {
        return this.messages.length > 1 || (this.messages.length === 1 && !isArtemisClientSentMessage(this.messages[0]));
    }

    createNewSession() {
        console.log('ExerciseId: ' + this.exerciseId + ' CourseId: ' + this.courseId);
        this.sessionService.createNewSession(this.exerciseId ?? this.courseId);
    }

    createNewCourseSession() {
        this.sessionService.createNewSession(this.courseId);
    }

    isChatSession() {
        return this.sessionService instanceof IrisChatSessionService;
    }

    isExerciseCreationSession() {
        return this.sessionService instanceof IrisExerciseCreationSessionService;
    }

    protected readonly IrisSender = IrisSender;
    protected readonly isInProgress = isInProgress;
    protected readonly getTextContent = getTextContent;
    protected readonly isTextContent = isTextContent;
    protected readonly isNotExecuted = isNotExecuted;
    protected readonly isExercisePlan = isExercisePlan;
    protected readonly isHidden = isHidden;
    protected readonly hideOrUnhide = hideOrUnhide;
    protected readonly isStudentSentMessage = isStudentSentMessage;
    protected readonly isServerSentMessage = isServerSentMessage;
    protected readonly isArtemisClientSentMessage = isArtemisClientSentMessage;
}
