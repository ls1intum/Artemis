import { __decorate, __metadata } from 'tslib';
import { Component, inject, ViewChild, ElementRef } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faXmark, faPaperPlane, faRobot, faUser } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AgentChatService } from './agent-chat.service';
let AgentChatModalComponent = class AgentChatModalComponent {
    messagesContainer;
    messageInput;
    closeIcon = faXmark;
    sendIcon = faPaperPlane;
    robotIcon = faRobot;
    userIcon = faUser;
    activeModal = inject(NgbActiveModal);
    agentChatService = inject(AgentChatService);
    courseId;
    messages = [];
    currentMessage = '';
    isAgentTyping = false;
    shouldScrollToBottom = false;
    ngOnInit() {
        this.addMessage("Hello! I'm your AI competency assistant. How can I help you with course competencies today?", false);
    }
    ngAfterViewChecked() {
        if (this.shouldScrollToBottom) {
            this.scrollToBottom();
            this.shouldScrollToBottom = false;
        }
    }
    closeModal() {
        this.activeModal.close();
    }
    sendMessage() {
        const message = this.currentMessage.trim();
        if (!message || this.isAgentTyping) {
            return;
        }
        this.addMessage(message, true);
        this.currentMessage = '';
        this.isAgentTyping = true;
        this.agentChatService.sendMessage(message, this.courseId).subscribe({
            next: (response) => {
                this.isAgentTyping = false;
                this.addMessage(response, false);
            },
            error: () => {
                this.isAgentTyping = false;
                this.addMessage("I apologize, but I'm having trouble processing your request right now. Please try again.", false);
            },
        });
    }
    onKeyPress(event) {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            this.sendMessage();
        }
    }
    onTextareaInput() {
        const textarea = this.messageInput.nativeElement;
        textarea.style.height = 'auto';
        textarea.style.height = Math.min(textarea.scrollHeight, 120) + 'px';
    }
    addMessage(content, isUser) {
        const message = {
            id: this.generateMessageId(),
            content,
            isUser,
            timestamp: new Date(),
        };
        this.messages.push(message);
        this.shouldScrollToBottom = true;
    }
    generateMessageId() {
        return Date.now().toString(36) + Math.random().toString(36).substr(2);
    }
    scrollToBottom() {
        if (this.messagesContainer) {
            const element = this.messagesContainer.nativeElement;
            element.scrollTop = element.scrollHeight;
        }
    }
};
__decorate([ViewChild('messagesContainer'), __metadata('design:type', ElementRef)], AgentChatModalComponent.prototype, 'messagesContainer', void 0);
__decorate([ViewChild('messageInput'), __metadata('design:type', ElementRef)], AgentChatModalComponent.prototype, 'messageInput', void 0);
AgentChatModalComponent = __decorate(
    [
        Component({
            selector: 'jhi-agent-chat-modal',
            standalone: true,
            imports: [CommonModule, TranslateDirective, FontAwesomeModule, FormsModule],
            templateUrl: './agent-chat-modal.component.html',
            styleUrl: './agent-chat-modal.component.scss',
        }),
    ],
    AgentChatModalComponent,
);
export { AgentChatModalComponent };
//# sourceMappingURL=agent-chat-modal.component.js.map
