import { ChangeDetectionStrategy, Component, effect, inject, input, viewChild } from '@angular/core';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisBaseChatbotComponent } from '../base-chatbot/iris-base-chatbot.component';

const EXERCISE_CHAT_MODES: Partial<Record<ExerciseType, ChatServiceMode>> = {
    [ExerciseType.TEXT]: ChatServiceMode.TEXT_EXERCISE,
    [ExerciseType.PROGRAMMING]: ChatServiceMode.PROGRAMMING_EXERCISE,
};

@Component({
    selector: 'jhi-course-chatbot',
    templateUrl: './course-chatbot.component.html',
    styleUrl: './course-chatbot.component.scss',
    imports: [IrisBaseChatbotComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseChatbotComponent {
    private readonly chatService = inject(IrisChatService);
    private readonly courseStorageService = inject(CourseStorageService);
    private readonly irisBaseChatbot = viewChild(IrisBaseChatbotComponent);

    readonly courseId = input<number>();
    /** Optional context type from handoff: 'lecture' or 'exercise'. */
    readonly initialContextType = input<string | undefined>(undefined);
    /** Entity ID corresponding to initialContextType. */
    readonly initialContextEntityId = input<number | undefined>(undefined);

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            if (courseId === undefined) return;

            this.chatService.setCourseId(courseId);

            const contextType = this.initialContextType();
            const entityId = this.initialContextEntityId();

            if (contextType === 'lecture' && entityId !== undefined) {
                this.chatService.switchTo(ChatServiceMode.LECTURE, entityId);
            } else if (contextType === 'exercise' && entityId !== undefined) {
                const mode = this.resolveExerciseMode(courseId, entityId);
                this.chatService.switchTo(mode ?? ChatServiceMode.COURSE, mode ? entityId : courseId);
            } else {
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

    private resolveExerciseMode(courseId: number, exerciseId: number): ChatServiceMode | undefined {
        const exercise = this.courseStorageService.getCourse(courseId)?.exercises?.find((e) => e.id === exerciseId);
        return exercise?.type ? EXERCISE_CHAT_MODES[exercise.type] : undefined;
    }
}
