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
        // Add a welcome message
        this.addMessage(this.translateService.instant('artemisApp.agent.chat.welcome'), false);
    }

    ngAfterViewInit(): void {
        // Auto-focus on textarea when modal opens
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

        // Add user message
        this.addMessage(message, true);
        this.currentMessage.set('');

        // Show typing indicator
        this.isAgentTyping.set(true);

        // Send message - backend will use courseId as conversationId for memory
        this.agentChatService.sendMessage(message, this.courseId).subscribe({
            next: (response) => {
                this.isAgentTyping.set(false);
                this.addMessage(response.message || this.translateService.instant('artemisApp.agent.chat.error'), false);

                // Emit event if competencies were modified so parent can refresh
                if (response.competenciesModified) {
                    this.competencyChanged.emit();
                }

                // Restore focus to input after agent responds - using Iris pattern
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
        // Prevent duplicate creation
        if (message.competencyCreated || !message.competencyPreview) {
            return;
        }

        // Create competency object from preview data
        const competency = new Competency();
        competency.title = message.competencyPreview.title;
        competency.description = message.competencyPreview.description;
        competency.taxonomy = message.competencyPreview.taxonomy;

        // Show typing indicator while creating
        this.isAgentTyping.set(true);

        // Call the competency service to create the competency
        this.competencyService.create(competency, this.courseId).subscribe({
            next: () => {
                this.isAgentTyping.set(false);

                // Mark this message's competency as created
                this.messages = this.messages.map((msg) => (msg.id === message.id ? { ...msg, competencyCreated: true } : msg));
                this.cdr.markForCheck();

                this.addMessage('Competency created successfully!', false);

                // Emit event to refresh competencies in parent component
                this.competencyChanged.emit();

                // Restore focus to input
                setTimeout(() => this.messageInput()?.nativeElement?.focus(), 10);
            },
            error: () => {
                this.isAgentTyping.set(false);
                this.addMessage('Failed to create competency. Please try again.', false);

                // Restore focus to input
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

        // Try to parse JSON to detect competency preview
        if (!isUser) {
            const previewData = this.extractCompetencyPreview(content);
            if (previewData) {
                message.competencyPreview = previewData.preview;
                message.content = previewData.cleanedMessage;
            }
        }

        this.messages = [...this.messages, message];
        this.shouldScrollToBottom = true;
        this.cdr.markForCheck();
    }

    /**
     * Attempts to extract competency preview data from the agent's response.
     * The backend returns JSON when the previewCompetency tool is called.
     */
    private extractCompetencyPreview(content: string): { preview: CompetencyPreview; cleanedMessage: string } | null {
        try {
            // Try to find JSON in the message, handling markdown code blocks
            // Pattern matches: ```json\n{...}\n``` or just {...}
            const jsonMatch = content.match(/```json\s*(\{[\s\S]*?\})\s*```|\{[\s\S]*?"preview"[\s\S]*?\}/);
            if (!jsonMatch) {
                return null;
            }

            // Extract the actual JSON (either from capture group or full match)
            const jsonString = jsonMatch[1] || jsonMatch[0];
            const jsonData = JSON.parse(jsonString);

            // Check if this is a preview response
            if (jsonData.preview === true && jsonData.competency) {
                const preview: CompetencyPreview = {
                    title: jsonData.competency.title,
                    description: jsonData.competency.description,
                    taxonomy: jsonData.competency.taxonomy as any,
                    icon: jsonData.competency.icon,
                };

                // Remove the entire JSON block (including markdown wrapper) from the message
                // Keep only the surrounding text, removing the technical message from the tool
                let cleanedMessage = content.replace(jsonMatch[0], '').trim();

                // Remove any leftover empty code blocks or extra whitespace
                cleanedMessage = cleanedMessage
                    .replace(/```json\s*```/g, '')
                    .replace(/\n{3,}/g, '\n\n')
                    .trim();

                return { preview, cleanedMessage };
            }
        } catch (e) {
            // Not valid JSON or not a preview, just return null
        }

        return null;
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
