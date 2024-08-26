import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { ChatServiceMode, IrisChatService } from 'app/iris/iris-chat.service';

@Component({
    selector: 'jhi-course-chatbot',
    templateUrl: './course-chatbot.component.html',
    styleUrl: './course-chatbot.component.scss',
})
export class CourseChatbotComponent implements OnChanges {
    @Input() courseId?: number;

    constructor(public chatService: IrisChatService) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.courseId) {
            this.chatService.switchTo(ChatServiceMode.COURSE, this.courseId);
        }
    }
}
