import { ChangeDetectionStrategy, Component, computed, effect, inject, input, viewChild } from '@angular/core';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisBaseChatbotComponent } from '../base-chatbot/iris-base-chatbot.component';

export interface LectureContextProvider {
    getCurrentPdfPage(): number | undefined;
    getCurrentVideoTimestamp(): number | undefined;
}

@Component({
    selector: 'jhi-lecture-chatbot',
    imports: [IrisBaseChatbotComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
        @if (lectureId()) {
            <jhi-iris-base-chatbot
                [showDeclineButton]="false"
                [isChatHistoryAvailable]="false"
                [layout]="'widget'"
                [aboutIrisDialogTransport]="'dynamic'"
                [contextLectureUnitId]="lectureUnitId()"
                [contextPdfPageProvider]="pdfPageProvider"
                [contextVideoTimestampProvider]="videoTimestampProvider"
            />
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

    /** Lecture unit identifier for context awareness. */
    readonly lectureUnitId = input<number | undefined>(undefined);

    /** Context provider for accessing current PDF page and video timestamp. */
    readonly contextProvider = input<LectureContextProvider | undefined>(undefined);

    /** Function provider for PDF page - passed to base chatbot */
    readonly pdfPageProvider = computed(() => {
        const provider = this.contextProvider();
        return provider ? () => provider.getCurrentPdfPage() : undefined;
    });

    /** Function provider for video timestamp - passed to base chatbot */
    readonly videoTimestampProvider = computed(() => {
        const provider = this.contextProvider();
        return provider ? () => provider.getCurrentVideoTimestamp() : undefined;
    });

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
