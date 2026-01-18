import { ChangeDetectionStrategy, Component, effect, inject, input } from '@angular/core';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisBaseChatbotComponent } from '../base-chatbot/iris-base-chatbot.component';

export type ContextType = 'course' | 'lecture' | 'exercise';

@Component({
    selector: 'jhi-course-chatbot',
    templateUrl: './course-chatbot.component.html',
    styleUrl: './course-chatbot.component.scss',
    imports: [IrisBaseChatbotComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseChatbotComponent {
    private readonly chatService = inject(IrisChatService);

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

    /**
     * Handles the context selection event from the base chatbot component.
     * @param context The selected context type
     */
    onContextSelected(context: ContextType): void {
        const courseId = this.courseId();
        if (courseId === undefined) {
            return;
        }

        switch (context) {
            case 'course':
                this.chatService.switchTo(ChatServiceMode.COURSE, courseId);
                break;
            case 'lecture':
                //Show list of lectures
                break;
            case 'exercise':
                //Show list of exercises
                break;
        }
    }
}
