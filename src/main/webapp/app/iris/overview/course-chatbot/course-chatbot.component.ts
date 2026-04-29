import { ChangeDetectionStrategy, Component, effect, inject, input, viewChild } from '@angular/core';
import { ChatServiceMode } from 'app/iris/shared/entities/iris-chat-mode.model';
import { IrisChatControllerService } from 'app/iris/overview/services/iris-chat-controller.service';
import { IrisBaseChatbotComponent } from '../base-chatbot/iris-base-chatbot.component';

@Component({
    selector: 'jhi-course-chatbot',
    templateUrl: './course-chatbot.component.html',
    styleUrl: './course-chatbot.component.scss',
    imports: [IrisBaseChatbotComponent],
    providers: [IrisChatControllerService],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseChatbotComponent {
    private readonly controller = inject(IrisChatControllerService);
    private readonly irisBaseChatbot = viewChild(IrisBaseChatbotComponent);

    readonly courseId = input<number>();

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            if (courseId !== undefined) {
                this.controller.setContext(courseId, ChatServiceMode.COURSE, courseId);
            }
        });
    }

    public toggleChatHistory(): void {
        const baseChatbot = this.irisBaseChatbot();
        if (!baseChatbot) {
            return;
        }
        baseChatbot.setChatHistoryVisibility(!baseChatbot.isChatHistoryOpen());
    }
}
