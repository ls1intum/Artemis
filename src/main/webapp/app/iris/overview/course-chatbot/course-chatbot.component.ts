import { ChangeDetectionStrategy, Component, effect, inject, input, viewChild } from '@angular/core';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisBaseChatbotComponent } from '../base-chatbot/iris-base-chatbot.component';

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
                this.chatService.switchTo(ChatServiceMode.COURSE, courseId);
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
