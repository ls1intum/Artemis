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
export class LectureChatbotComponent {
    private readonly chatService = inject(IrisChatService);
    private readonly irisBaseChatbot = viewChild(IrisBaseChatbotComponent);

    readonly lectureId = input<number>();

    constructor() {
        effect(() => {
            const lectureId = this.lectureId();
            if (lectureId !== undefined) {
                this.chatService.switchTo(ChatServiceMode.LECTURE, lectureId);
            }
        });
    }

    public toggleChatHistory(): void {
        const baseChatbot = this.irisBaseChatbot();
        if (baseChatbot) {
            baseChatbot.setChatHistoryVisibility(!baseChatbot.isChatHistoryOpen());
        }
    }
}
