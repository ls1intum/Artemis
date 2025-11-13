import {
    AfterViewChecked,
    AfterViewInit,
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    ElementRef,
    OnInit,
    computed,
    inject,
    output,
    signal,
    viewChild,
} from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faPaperPlane, faRobot, faUser } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { firstValueFrom } from 'rxjs';
import { AgentChatService } from './agent-chat.service';
import { ChatMessage, CompetencyPreview } from 'app/atlas/shared/entities/chat-message.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CompetencyCardComponent } from 'app/atlas/overview/competency-card/competency-card.component';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { Competency } from 'app/atlas/shared/entities/competency.model';

@Component({
    selector: 'jhi-agent-chat-modal',
    standalone: true,
    imports: [CommonModule, TranslateDirective, FontAwesomeModule, FormsModule, ArtemisTranslatePipe, CompetencyCardComponent],
    templateUrl: './agent-chat-modal.component.html',
    styleUrl: './agent-chat-modal.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgentChatModalComponent implements OnInit, AfterViewInit, AfterViewChecked {
    private readonly messagesContainer = viewChild.required<ElementRef>('messagesContainer');
    private readonly messageInput = viewChild.required<ElementRef<HTMLTextAreaElement>>('messageInput');

    protected readonly sendIcon = faPaperPlane;
    protected readonly robotIcon = faRobot;
    protected readonly userIcon = faUser;

    private readonly activeModal = inject(NgbActiveModal);
    private readonly agentChatService = inject(AgentChatService);
    private readonly competencyService = inject(CompetencyService);
    private readonly translateService = inject(TranslateService);
    private readonly cdr = inject(ChangeDetectorRef);

    courseId!: number;
    messages: ChatMessage[] = [];
    currentMessage = signal('');
    isAgentTyping = signal(false);
    private shouldScrollToBottom = false;

    // Event emitted when agent likely created/modified competencies
    competencyChanged = output<void>();

    // Message validation
    readonly MAX_MESSAGE_LENGTH = 8000;

    currentMessageLength = computed(() => this.currentMessage().length);
    isMessageTooLong = computed(() => this.currentMessageLength() > this.MAX_MESSAGE_LENGTH);
    canSendMessage = computed(() => {
        const message = this.currentMessage().trim();
        return !!(message && !this.isAgentTyping() && !this.isMessageTooLong());
    });

    ngOnInit(): void {
        this.addMessage(this.translateService.instant('artemisApp.agent.chat.welcome'), false);
        // Load previous conversation history
        this.agentChatService.getConversationHistory(this.courseId).subscribe({
            next: (history) => {
                history.forEach((msg) => {
                    if (msg.competencyPreview || msg.batchCompetencyPreview) {
                        this.addMessageWithPreview(msg.content, msg.isUser, msg.competencyPreview, msg.batchCompetencyPreview);
                    } else {
                        this.addMessage(msg.content, msg.isUser);
                    }
                });
            },
            error: () => {
                this.addMessage(this.translateService.instant('artemisApp.agent.chat.welcome'), false);
            },
        });
    }

    ngAfterViewInit(): void {
        setTimeout(() => this.messageInput()?.nativeElement?.focus(), 10);
    }

    ngAfterViewChecked(): void {
        if (this.shouldScrollToBottom) {
            this.scrollToBottom();
            this.shouldScrollToBottom = false;
        }
    }

    protected closeModal(): void {
        this.activeModal.close();
    }

    protected sendMessage(): void {
        const message = this.currentMessage().trim();
        if (!this.canSendMessage()) {
            return;
        }

        // Invalidate any pending plan approvals when user sends a new message
        // This means they're refining the plan or moving to a different topic
        this.invalidatePendingPlanApprovals();

        this.addMessage(message, true);
        this.currentMessage.set('');

        this.isAgentTyping.set(true);

        this.agentChatService.sendMessage(message, this.courseId).subscribe({
            next: (response) => {
                this.isAgentTyping.set(false);

                this.addMessageWithPreview(
                    response.message || this.translateService.instant('artemisApp.agent.chat.error'),
                    false,
                    response.competencyPreview,
                    response.batchCompetencyPreview,
                );

                if (response.competenciesModified) {
                    this.competencyChanged.emit();
                }

                setTimeout(() => this.messageInput()?.nativeElement?.focus(), 10);
            },
            error: () => {
                this.isAgentTyping.set(false);
                this.addMessage(this.translateService.instant('artemisApp.agent.chat.error'), false);
                // Restore focus to input after error
                setTimeout(() => this.messageInput()?.nativeElement?.focus(), 10);
            },
        });
    }

    onKeyPress(event: KeyboardEvent): void {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            this.sendMessage();
        }
    }

    onTextareaInput(): void {
        // Auto-resize textarea
        if (!this.messageInput()?.nativeElement) {
            return;
        }
        const textarea = this.messageInput().nativeElement;
        textarea.style.height = 'auto';
        textarea.style.height = Math.min(textarea.scrollHeight, 120) + 'px';
    }

    protected onCreateCompetency(message: ChatMessage): void {
        // Prevent duplicate creation/update
        if (message.competencyCreated || !message.competencyPreview) {
            return;
        }

        // Create competency object from preview data
        const competency = new Competency();
        competency.title = message.competencyPreview.title;
        competency.description = message.competencyPreview.description;
        competency.taxonomy = message.competencyPreview.taxonomy;

        // Check if this is an update or create operation
        const isUpdate = message.competencyPreview.competencyId !== undefined;
        if (isUpdate) {
            competency.id = message.competencyPreview.competencyId;
        }

        this.isAgentTyping.set(true);

        // Call the competency service to create or update the competency
        const operation = isUpdate ? this.competencyService.update(competency, this.courseId) : this.competencyService.create(competency, this.courseId);

        operation.subscribe({
            next: () => {
                this.isAgentTyping.set(false);

                // Mark this message's competency as created/updated
                this.messages = this.messages.map((msg) => (msg.id === message.id ? { ...msg, competencyCreated: true } : msg));
                this.cdr.markForCheck();

                const translationKey = isUpdate ? 'artemisApp.agent.chat.success.updatedSingle' : 'artemisApp.agent.chat.success.createdSingle';

                this.addMessage(this.translateService.instant(translationKey), false);

                // Emit event to refresh competencies in parent component
                this.competencyChanged.emit();

                // Restore focus to input
                setTimeout(() => this.messageInput()?.nativeElement?.focus(), 10);
            },
            error: () => {
                this.isAgentTyping.set(false);
                const errorKey = isUpdate ? 'artemisApp.agent.chat.error.updateFailed' : 'artemisApp.agent.chat.error.createFailed';
                this.addMessage(this.translateService.instant(errorKey), false);

                // Restore focus to input
                setTimeout(() => this.messageInput()?.nativeElement?.focus(), 10);
            },
        });
    }

    protected onApprovePlan(message: ChatMessage): void {
        // Prevent duplicate approval
        if (message.planApproved) {
            return;
        }

        // Mark this message's plan as approved
        this.messages = this.messages.map((msg) => (msg.id === message.id ? { ...msg, planApproved: true, planPending: false } : msg));
        this.cdr.markForCheck();

        // Send approval message to agent
        this.addMessage('I approve the plan', true);
        this.isAgentTyping.set(true);

        // Send approval - backend will process it
        this.agentChatService.sendMessage(this.translateService.instant('artemisApp.agent.chat.planApproval'), this.courseId).subscribe({
            next: (response) => {
                this.isAgentTyping.set(false);
                this.addMessage(response.message || this.translateService.instant('artemisApp.agent.chat.error'), false);

                // Emit event if competencies were modified
                if (response.competenciesModified) {
                    this.competencyChanged.emit();
                }

                // Restore focus to input
                setTimeout(() => this.messageInput()?.nativeElement?.focus(), 10);
            },
            error: () => {
                this.isAgentTyping.set(false);
                this.addMessage(this.translateService.instant('artemisApp.agent.chat.error'), false);
                setTimeout(() => this.messageInput()?.nativeElement?.focus(), 10);
            },
        });
    }

    private addMessage(content: string, isUser: boolean): void {
        const message: ChatMessage = {
            id: this.generateMessageId(),
            content,
            isUser,
            timestamp: new Date(),
        };

        this.finalizeMessage(message, isUser);
    }

    /**
     * Add a message with structured preview data from the server.
     * This method receives clean preview data as DTOs instead of parsing JSON.
     */
    private addMessageWithPreview(
        content: string,
        isUser: boolean,
        singlePreview?: { preview: boolean; competency: CompetencyPreview; competencyId?: number; viewOnly?: boolean },
        batchPreview?: { batchPreview: boolean; count: number; competencies: CompetencyPreview[]; viewOnly?: boolean },
    ): void {
        const message: ChatMessage = {
            id: this.generateMessageId(),
            content,
            isUser,
            timestamp: new Date(),
        };

        if (singlePreview?.preview) {
            message.competencyPreview = {
                ...singlePreview.competency,
                competencyId: singlePreview.competencyId,
                viewOnly: singlePreview.viewOnly,
            };
        }

        if (batchPreview?.batchPreview) {
            message.batchCompetencyPreview = batchPreview.competencies.map((comp) => ({
                ...comp,
                viewOnly: batchPreview.viewOnly,
            }));
        }

        this.finalizeMessage(message, isUser);
    }

    /**
     * Finalizes a message before displaying it:
     * - Detects plan approval markers
     * - Appends it to the message list
     * - Marks for scroll and change detection
     */
    private finalizeMessage(message: ChatMessage, isUser: boolean): void {
        // Detect [PLAN_PENDING] markers and clean message text
        if (!isUser) {
            const planPendingData = this.extractPlanPending(message.content);
            if (planPendingData) {
                message.planPending = true;
                message.content = planPendingData.cleanedMessage;
            }
        }

        // Append message and trigger UI updates
        this.messages = [...this.messages, message];
        this.shouldScrollToBottom = true;
        this.cdr.markForCheck();
    }

    /**
     * Detects [PLAN_PENDING] marker in agent responses.
     * This marker indicates that the agent has proposed a plan and is awaiting approval.
     */
    private extractPlanPending(content: string): { cleanedMessage: string } | null {
        if (!content) {
            return null;
        }
        const planPendingMarker = '[PLAN_PENDING]';
        const escapedPlanPendingMarker = '\\[PLAN_PENDING\\]';

        // Check for both escaped and unescaped markers
        if (content.includes(planPendingMarker)) {
            // Remove the marker from the message
            const cleanedMessage = content.replace(planPendingMarker, '').trim();
            return { cleanedMessage };
        }

        if (content.includes(escapedPlanPendingMarker)) {
            // Remove the escaped marker from the message
            const cleanedMessage = content.replace(escapedPlanPendingMarker, '').trim();
            return { cleanedMessage };
        }

        return null;
    }

    /**
     * Handles batch competency creation/update.
     * Creates or updates all competencies in the batch preview.
     */
    protected onCreateBatchCompetencies(message: ChatMessage): void {
        // Prevent duplicate creation
        if (message.batchCreated || !message.batchCompetencyPreview || message.batchCompetencyPreview.length === 0) {
            return;
        }

        // Show typing indicator
        this.isAgentTyping.set(true);

        // Build operations array
        const operations = message.batchCompetencyPreview.map((preview) => {
            const competency = new Competency();
            competency.title = preview.title;
            competency.description = preview.description;
            competency.taxonomy = preview.taxonomy;
            if (preview.competencyId !== undefined) {
                competency.id = preview.competencyId;
            }
            return competency;
        });

        // Execute all operations
        const promises = operations.map((competency) => {
            const isUpdate = competency.id !== undefined;
            const operation = isUpdate ? this.competencyService.update(competency, this.courseId) : this.competencyService.create(competency, this.courseId);
            return firstValueFrom(operation);
        });

        Promise.all(promises)
            .then(() => {
                this.isAgentTyping.set(false);

                // Mark batch as created
                this.messages = this.messages.map((msg) => (msg.id === message.id ? { ...msg, batchCreated: true } : msg));
                this.cdr.markForCheck();

                const count = operations.length;
                const hasUpdates = operations.some((op) => op.id !== undefined);
                const hasCreates = operations.some((op) => op.id === undefined);

                let successMessage;
                if (hasUpdates && hasCreates) {
                    successMessage = this.translateService.instant('artemisApp.agent.chat.success.processed', { count });
                } else if (hasUpdates) {
                    successMessage = this.translateService.instant('artemisApp.agent.chat.success.updated', { count });
                } else {
                    successMessage = this.translateService.instant('artemisApp.agent.chat.success.created', { count });
                }

                this.addMessage(successMessage, false);

                // Emit event to refresh competencies
                this.competencyChanged.emit();

                // Restore focus
                setTimeout(() => this.messageInput()?.nativeElement?.focus(), 10);
            })
            .catch(() => {
                this.isAgentTyping.set(false);
                this.addMessage(this.translateService.instant('artemisApp.agent.chat.competencyProcessFailure'), false);
                setTimeout(() => this.messageInput()?.nativeElement?.focus(), 10);
            });
    }

    /**
     * Invalidates all pending plan approvals.
     * Called when the user sends a new message (refining the plan) or when workflow moves forward.
     * This disables the "Approve Plan" button for previous plan proposals.
     */
    private invalidatePendingPlanApprovals(): void {
        this.messages = this.messages.map((msg) => {
            // If a message has a pending plan that hasn't been approved yet, mark it as no longer pending
            if (msg.planPending && !msg.planApproved) {
                return { ...msg, planPending: false };
            }
            return msg;
        });
        this.cdr.markForCheck();
    }

    private generateMessageId(): string {
        return Date.now().toString(36) + Math.random().toString(36).slice(2);
    }

    private scrollToBottom(): void {
        if (this.messagesContainer()) {
            const element = this.messagesContainer().nativeElement;
            element.scrollTop = element.scrollHeight;
        }
    }
}
