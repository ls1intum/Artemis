import { AfterViewChecked, AfterViewInit, Component, ElementRef, OnInit, computed, inject, output, signal, viewChild } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faPaperPlane, faRobot, faUser } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { firstValueFrom } from 'rxjs';
import { AgentChatService, CompetencyPreviewResponse, CompetencyRelationPreviewResponse } from '../services/agent-chat.service';
import { ChatMessage } from 'app/atlas/shared/entities/chat-message.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CompetencyCardComponent } from 'app/atlas/overview/competency-card/competency-card.component';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { Competency } from 'app/atlas/shared/entities/competency.model';
import { NgxGraphModule } from '@swimlane/ngx-graph';

@Component({
    selector: 'jhi-agent-chat-modal',
    standalone: true,
    imports: [CommonModule, TranslateDirective, FontAwesomeModule, FormsModule, ArtemisTranslatePipe, CompetencyCardComponent, NgxGraphModule],
    templateUrl: './agent-chat-modal.component.html',
    styleUrl: './agent-chat-modal.component.scss',
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

    courseId!: number;
    messages = signal<ChatMessage[]>([]);
    currentMessage = signal('');
    isAgentTyping = signal(false);
    shouldScrollToBottom = signal(false);

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
        this.agentChatService.getConversationHistory(this.courseId).subscribe({
            next: (history) => {
                if (history.length === 0) {
                    this.addMessage(this.translateService.instant('artemisApp.agent.chat.welcome'), false);
                }
                history.forEach((msg) => {
                    this.addMessage(msg.content, msg.isUser, msg.competencyPreviews, msg.relationPreviews);
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
        if (this.shouldScrollToBottom()) {
            this.scrollToBottom();
            this.shouldScrollToBottom.set(false);
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
        this.invalidatePendingPlanApprovals();

        this.addMessage(message, true);
        this.currentMessage.set('');

        this.resetTextareaHeight();

        this.isAgentTyping.set(true);

        this.agentChatService.sendMessage(message, this.courseId).subscribe({
            next: (response) => {
                this.isAgentTyping.set(false);

                this.addMessage(response.message || this.translateService.instant('artemisApp.agent.chat.error'), false, response.competencyPreviews, response.relationPreviews);

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

    /**
     * Handles competency creation/update for both single and multiple competencies.
     * Uses the unified competencyPreviews array.
     */
    onCreateCompetencies(message: ChatMessage): void {
        if (message.competencyCreated || !message.competencyPreviews || message.competencyPreviews.length === 0) {
            return;
        }

        this.isAgentTyping.set(true);

        const competencies = message.competencyPreviews.map((preview) => {
            const competency = new Competency();
            competency.title = preview.title;
            competency.description = preview.description;
            competency.taxonomy = preview.taxonomy;
            if (preview.competencyId !== undefined) {
                competency.id = preview.competencyId;
            }
            return competency;
        });

        const promises = competencies.map((competency) => {
            const isUpdate = competency.id !== undefined;
            const operation = isUpdate ? this.competencyService.update(competency, this.courseId) : this.competencyService.create(competency, this.courseId);
            return firstValueFrom(operation);
        });

        Promise.all(promises)
            .then(() => {
                this.isAgentTyping.set(false);

                this.messages.update((msgs) => msgs.map((msg) => (msg.id === message.id ? { ...msg, competencyCreated: true } : msg)));

                const count = competencies.length;
                const hasUpdates = competencies.some((comp) => comp.id !== undefined);
                const hasCreates = competencies.some((comp) => comp.id === undefined);

                let successMessage;
                if (count === 1) {
                    // Single competency
                    const translationKey = hasUpdates ? 'artemisApp.agent.chat.success.updatedSingle' : 'artemisApp.agent.chat.success.createdSingle';
                    successMessage = this.translateService.instant(translationKey);
                } else {
                    if (hasUpdates && hasCreates) {
                        successMessage = this.translateService.instant('artemisApp.agent.chat.success.processed', { count });
                    } else if (hasUpdates) {
                        successMessage = this.translateService.instant('artemisApp.agent.chat.success.updated', { count });
                    } else {
                        successMessage = this.translateService.instant('artemisApp.agent.chat.success.created', { count });
                    }
                }

                this.addMessage(successMessage, false);

                this.competencyChanged.emit();

                setTimeout(() => this.messageInput()?.nativeElement?.focus(), 10);
            })
            .catch(() => {
                this.isAgentTyping.set(false);
                this.addMessage(this.translateService.instant('artemisApp.agent.chat.competencyProcessFailure'), false);
                setTimeout(() => this.messageInput()?.nativeElement?.focus(), 10);
            });
    }

    protected onCreateRelation(message: ChatMessage): void {
        // Prevent duplicate creation
        if (message.relationCreated || !message.relationPreview) {
            return;
        }

        this.isAgentTyping.set(true);

        // Trigger relation creation via agent
        this.agentChatService.sendMessage('Create the relation', this.courseId).subscribe({
            next: () => {
                this.isAgentTyping.set(false);

                // Mark this message's relation as created
                this.messages.update((msgs) => msgs.map((msg) => (msg.id === message.id ? { ...msg, relationCreated: true } : msg)));

                this.addMessage(this.translateService.instant('artemisApp.agent.chat.success.relationCreated'), false);

                // Emit event to refresh competencies (relations affect the graph)
                this.competencyChanged.emit();

                // Restore focus to input
                setTimeout(() => this.messageInput()?.nativeElement?.focus(), 10);
            },
            error: () => {
                this.isAgentTyping.set(false);
                this.addMessage(this.translateService.instant('artemisApp.agent.chat.error.relationCreateFailed'), false);

                // Restore focus to input
                setTimeout(() => this.messageInput()?.nativeElement?.focus(), 10);
            },
        });
    }

    protected onApprovePlan(message: ChatMessage): void {
        if (message.planApproved) {
            return;
        }

        this.messages.update((msgs) => msgs.map((msg) => (msg.id === message.id ? { ...msg, planApproved: true, planPending: false } : msg)));

        this.addMessage(this.translateService.instant('artemisApp.agent.chat.approvePlan'), false);
        this.isAgentTyping.set(true);

        this.agentChatService.sendMessage(this.translateService.instant('artemisApp.agent.chat.planApproval'), this.courseId).subscribe({
            next: (response) => {
                this.isAgentTyping.set(false);
                this.addMessage(response.message || this.translateService.instant('artemisApp.agent.chat.error'), false);

                if (response.competenciesModified) {
                    this.competencyChanged.emit();
                }

                setTimeout(() => this.messageInput()?.nativeElement?.focus(), 10);
            },
            error: () => {
                this.isAgentTyping.set(false);
                this.addMessage(this.translateService.instant('artemisApp.agent.chat.error'), false);
                setTimeout(() => this.messageInput()?.nativeElement?.focus(), 10);
            },
        });
    }

    /**
     * Adds a message to the chat with optional competency and relation preview data.
     * Uses unified array-based approach for both competencies and relations (similar to competency cards).
     * Handles plan pending markers, preview data mapping, and automatic scrolling.
     */
    private addMessage(content: string, isUser: boolean, competencyPreviews?: CompetencyPreviewResponse[], relationPreviews?: CompetencyRelationPreviewResponse[]): void {
        const message: ChatMessage = {
            id: this.generateMessageId(),
            content,
            isUser,
            timestamp: new Date(),
        };

        if (competencyPreviews && competencyPreviews.length > 0) {
            message.competencyPreviews = competencyPreviews.map((preview) => ({
                title: preview.title,
                description: preview.description,
                taxonomy: preview.taxonomy,
                icon: preview.icon,
                competencyId: preview.competencyId,
                viewOnly: preview.viewOnly,
            }));
        }

        if (relationPreviews && relationPreviews.length > 0) {
            message.relationPreviews = relationPreviews.map((preview) => ({
                relationId: preview.relationId,
                headCompetencyId: preview.headCompetencyId,
                headCompetencyTitle: preview.headCompetencyTitle,
                tailCompetencyId: preview.tailCompetencyId,
                tailCompetencyTitle: preview.tailCompetencyTitle,
                relationType: preview.relationType,
                viewOnly: preview.viewOnly,
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
            const cleanedContent = this.removePlanPendingMarkerFromMessageContent(message.content);
            if (cleanedContent !== undefined) {
                message.planPending = true;
                message.content = cleanedContent;
            }
        }

        this.messages.update((msgs) => [...msgs, message]);
        this.shouldScrollToBottom.set(true);
    }

    /**
     * Detects [PLAN_PENDING] marker in agent responses.
     * This marker indicates that the agent has proposed a plan and is awaiting approval.
     */
    private removePlanPendingMarkerFromMessageContent(content: string): string | undefined {
        if (!content) {
            return undefined;
        }
        const planPendingMarker = '[PLAN_PENDING]';
        const escapedPlanPendingMarker = '\\[PLAN_PENDING\\]';

        if (content.includes(planPendingMarker)) {
            return content.replace(planPendingMarker, '').trim();
        }

        if (content.includes(escapedPlanPendingMarker)) {
            return content.replace(escapedPlanPendingMarker, '').trim();
        }

        return undefined;
    }

    /**
     * Invalidates all pending plan approvals.
     * Called when the user sends a new message (refining the plan) or when workflow moves forward.
     * This disables the "Approve Plan" button for previous plan proposals.
     */
    private invalidatePendingPlanApprovals(): void {
        this.messages.update((msgs) =>
            msgs.map((msg) => {
                if (msg.planPending && !msg.planApproved) {
                    return { ...msg, planPending: false };
                }
                return msg;
            }),
        );
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

    private resetTextareaHeight(): void {
        if (this.messageInput()?.nativeElement) {
            const textarea = this.messageInput().nativeElement;
            textarea.style.height = 'auto';
        }
    }
}
