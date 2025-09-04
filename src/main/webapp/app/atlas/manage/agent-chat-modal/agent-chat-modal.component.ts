import { AfterViewChecked, AfterViewInit, Component, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faPaperPlane, faRobot, faUser } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { CommonModule, DatePipe } from '@angular/common';
import { AgentChatService } from './agent-chat.service';
import { ChatMessage } from 'app/atlas/shared/entities/chat-message.model';

@Component({
    selector: 'jhi-agent-chat-modal',
    standalone: true,
    imports: [CommonModule, DatePipe, TranslateDirective, FontAwesomeModule, FormsModule],
    templateUrl: './agent-chat-modal.component.html',
    styleUrl: './agent-chat-modal.component.scss',
})
export class AgentChatModalComponent implements OnInit, AfterViewInit, AfterViewChecked {
    @ViewChild('messagesContainer') private messagesContainer!: ElementRef;
    @ViewChild('messageInput') private messageInput!: ElementRef<HTMLTextAreaElement>;

    protected readonly sendIcon = faPaperPlane;
    protected readonly robotIcon = faRobot;
    protected readonly userIcon = faUser;

    private readonly activeModal = inject(NgbActiveModal);
    private readonly agentChatService = inject(AgentChatService);

    courseId!: number;
    messages: ChatMessage[] = [];
    currentMessage = '';
    isAgentTyping = false;
    private shouldScrollToBottom = false;

    ngOnInit(): void {
        // Add a welcome message
        this.addMessage("Hello! I'm your AI competency assistant. How can I help you with course competencies today?", false);
    }

    ngAfterViewInit(): void {
        // Auto-focus on textarea when modal opens - using same pattern as Iris
        setTimeout(() => this.messageInput?.nativeElement?.focus(), 10);
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

    protected onModalBackdropClick(event: MouseEvent): void {
        if (event.target === event.currentTarget) {
            this.closeModal();
        }
    }

    protected sendMessage(): void {
        const message = this.currentMessage.trim();
        if (!message || this.isAgentTyping) {
            return;
        }

        // Add user message
        this.addMessage(message, true);
        this.currentMessage = '';

        // Show typing indicator
        this.isAgentTyping = true;

        // Simulate agent response (mock for now)
        this.agentChatService.sendMessage(message, this.courseId).subscribe({
            next: (response) => {
                this.isAgentTyping = false;
                this.addMessage(response, false);
                // Restore focus to input after agent responds - using Iris pattern
                setTimeout(() => this.messageInput?.nativeElement?.focus(), 10);
            },
            error: () => {
                this.isAgentTyping = false;
                this.addMessage("I apologize, but I'm having trouble processing your request right now. Please try again.", false);
                // Restore focus to input after error - using Iris pattern
                setTimeout(() => this.messageInput?.nativeElement?.focus(), 10);
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
        if (!this.messageInput?.nativeElement) {
            return;
        }
        const textarea = this.messageInput.nativeElement;
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
        this.messages.push(message);
        this.shouldScrollToBottom = true;
    }

    private generateMessageId(): string {
        return Date.now().toString(36) + Math.random().toString(36).slice(2);
    }

    private scrollToBottom(): void {
        if (this.messagesContainer) {
            const element = this.messagesContainer.nativeElement;
            element.scrollTop = element.scrollHeight;
        }
    }
}
