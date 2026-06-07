import { ChangeDetectionStrategy, Component, effect, inject, input, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
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
                [messagePrefixProvider]="buildContextBlock"
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

    /** Lecture unit identifier to include in context block. */
    readonly lectureUnitId = input<number | undefined>(undefined);

    /** Context provider for accessing current PDF page and video timestamp. */
    readonly contextProvider = input<LectureContextProvider | undefined>(undefined);

    /** Current chat mode to determine when to include context blocks. */
    private readonly currentMode = toSignal(this.chatService.currentChatMode(), { initialValue: undefined });

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

    /**
     * Builds the context block to prefix messages with lecture context.
     * Format: [context:lectureUnitId:page:timestamp]
     * This is passed as a function reference to be called when sending messages.
     * Only generates context block when in LECTURE chat mode.
     */
    readonly buildContextBlock = (): string => {
        // Only include context block when in LECTURE mode, not COURSE or EXERCISE modes
        if (this.currentMode() !== ChatServiceMode.LECTURE) {
            return '';
        }

        const unitId = this.lectureUnitId();
        const provider = this.contextProvider();

        if (!unitId || !provider) {
            return '';
        }

        const page = provider.getCurrentPdfPage() ?? '';
        const timestamp = provider.getCurrentVideoTimestamp() ?? '';

        return `[context:${unitId}:${page}:${timestamp}]`;
    };
}
