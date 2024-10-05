import { Component, Input, OnChanges, SimpleChanges, inject } from '@angular/core';
import { ChatServiceMode, IrisChatService } from 'app/iris/iris-chat.service';

@Component({
    selector: 'jhi-course-chatbot',
    templateUrl: './course-chatbot.component.html',
    styleUrl: './course-chatbot.component.scss',
})
export class CourseChatbotComponent implements OnChanges {
    chatService = inject(IrisChatService);

    @Input() courseId?: number;

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.courseId) {
            this.chatService.switchTo(ChatServiceMode.COURSE, this.courseId);
        }
    }
}
