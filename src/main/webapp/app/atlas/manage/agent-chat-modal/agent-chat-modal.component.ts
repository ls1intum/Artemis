import { AfterViewChecked, AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faPaperPlane, faRobot, faUser } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { CommonModule, DatePipe } from '@angular/common';
import { AgentChatService } from './agent-chat.service';
import { ChatMessage } from 'app/atlas/shared/entities/chat-message.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-agent-chat-modal',
    standalone: true,
    imports: [CommonModule, DatePipe, TranslateDirective, FontAwesomeModule, FormsModule, ArtemisTranslatePipe],
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
    private readonly translateService = inject(TranslateService);
    private readonly cdr = inject(ChangeDetectorRef);

    courseId!: number;
    messages: ChatMessage[] = [];
    currentMessage = signal('');
    isAgentTyping = signal(false);
    private shouldScrollToBottom = false;
    private sessionId!: string;

    // Message validation
    readonly MAX_MESSAGE_LENGTH = 8000;

    currentMessageLength = computed(() => this.currentMessage().length);
    isMessageTooLong = computed(() => this.currentMessageLength() > this.MAX_MESSAGE_LENGTH);
    canSendMessage = computed(() => {
        const message = this.currentMessage().trim();
        return !!(message && !this.isAgentTyping() && !this.isMessageTooLong());
    });

    ngOnInit(): void {
        this.sessionId = `course_${this.courseId}_session_${Date.now()}`;

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

        // Send message with session ID for continuity
        this.agentChatService.sendMessage(message, this.courseId, this.sessionId).subscribe({
            next: (response) => {
                this.isAgentTyping.set(false);
                this.addMessage(response, false);
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

    private addMessage(content: string, isUser: boolean): void {
        const message: ChatMessage = {
            id: this.generateMessageId(),
            content,
            isUser,
            timestamp: new Date(),
        };
        this.messages = [...this.messages, message];
        this.shouldScrollToBottom = true;
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
