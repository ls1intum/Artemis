import { ChangeDetectionStrategy, Component, effect, inject, input, viewChild } from '@angular/core';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisBaseChatbotComponent } from '../base-chatbot/iris-base-chatbot.component';

const CONTEXT_STORAGE_KEY_PREFIX = 'iris-context-';

@Component({
    selector: 'jhi-course-chatbot',
    templateUrl: './course-chatbot.component.html',
    styleUrl: './course-chatbot.component.scss',
    imports: [IrisBaseChatbotComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseChatbotComponent {
    private readonly chatService = inject(IrisChatService);
    private readonly irisBaseChatbot = viewChild(IrisBaseChatbotComponent);

    readonly courseId = input<number>();

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            if (courseId !== undefined) {
                this.chatService.setCourseId(courseId);
                const stored = this.getStoredContext(courseId);
                if (stored) {
                    this.chatService.switchTo(stored.mode, stored.entityId);
                } else {
                    this.chatService.switchTo(ChatServiceMode.COURSE, courseId);
                }
            }
        });
    }

    private getStoredContext(courseId: number): { mode: ChatServiceMode; entityId: number } | undefined {
        try {
            const raw = sessionStorage.getItem(CONTEXT_STORAGE_KEY_PREFIX + courseId);
            if (raw) {
                const parsed = JSON.parse(raw);
                if (parsed.mode && parsed.entityId !== undefined) {
                    return parsed;
                }
            }
        } catch {
            /* ignore invalid storage data */
        }
        return undefined;
    }

    public toggleChatHistory(): void {
        const baseChatbot = this.irisBaseChatbot();
        if (!baseChatbot) {
            return;
        }
        baseChatbot.setChatHistoryVisibility(!baseChatbot.isChatHistoryOpen());
    }
}
