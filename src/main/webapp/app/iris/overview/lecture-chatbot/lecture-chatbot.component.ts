import { ChangeDetectionStrategy, Component, effect, inject, input, viewChild } from '@angular/core';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisBaseChatbotComponent } from '../base-chatbot/iris-base-chatbot.component';

@Component({
    selector: 'jhi-lecture-chatbot',
    imports: [IrisBaseChatbotComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
        @if (lectureId()) {
            <jhi-iris-base-chatbot [showDeclineButton]="false" [isChatHistoryAvailable]="false" [layout]="'widget'" />
        }
    `,
    styles: [
        `
            :host {
                display: flex;
                flex-direction: column;
                height: 100%;
                overflow: hidden;
            }
        `,
    ],
})
/**
 * Lightweight lecture-specific wrapper around the shared Iris chatbot widget.
 */
export class LectureChatbotComponent {
    private readonly chatService = inject(IrisChatService);
    private readonly irisBaseChatbot = viewChild(IrisBaseChatbotComponent);

    /** Lecture identifier used to scope chatbot requests to the current lecture context. */
    readonly lectureId = input<number>();

    constructor() {
        // Keep chat service mode aligned with the currently displayed lecture.
        effect(() => {
            const lectureId = this.lectureId();
            if (lectureId !== undefined) {
                this.chatService.switchTo(ChatServiceMode.LECTURE, lectureId);
            }
        });
    }

    /** Toggles the visibility of the chat history panel in the embedded base chatbot. */
    public toggleChatHistory(): void {
        const baseChatbot = this.irisBaseChatbot();
        if (baseChatbot) {
            baseChatbot.setChatHistoryVisibility(!baseChatbot.isChatHistoryOpen());
        }
    }
}
