import { Component, Input, OnChanges, SimpleChanges, inject } from '@angular/core';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisBaseChatbotComponent } from '../base-chatbot/iris-base-chatbot.component';

@Component({
    selector: 'jhi-course-chatbot',
    templateUrl: './course-chatbot.component.html',
    styleUrl: './course-chatbot.component.scss',
    imports: [IrisBaseChatbotComponent],
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
