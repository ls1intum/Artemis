import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { IrisCourseChatService } from 'app/iris/iris-course-chat.service';

@Component({
    selector: 'jhi-course-chatbot',
    templateUrl: './course-chatbot.component.html',
    styleUrl: './course-chatbot.component.scss',
})
export class CourseChatbotComponent implements OnChanges {
    @Input() courseId?: number;

    constructor(public chatService: IrisCourseChatService) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.courseId) {
            this.chatService.changeToCourse(this.courseId);
        }
    }
}
