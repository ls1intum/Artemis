import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisBaseChatbotComponent } from '../base-chatbot/iris-base-chatbot.component';
import { ContextType } from 'app/iris/shared/context-selection/context-selection.component';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';

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
    readonly selectedContext = signal<ContextType>('course');
    readonly selectedLecture = signal<Lecture | undefined>(undefined);
    readonly selectedExercise = signal<Exercise | undefined>(undefined);

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            if (courseId !== undefined) {
                this.chatService.setCourseId(courseId);
            }
        });
    }
}
