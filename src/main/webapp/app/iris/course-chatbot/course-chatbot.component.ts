import { Component, OnChanges, SimpleChanges, input } from '@angular/core';
import { ChatServiceMode, IrisChatService } from 'app/iris/iris-chat.service';
import { IrisModule } from 'app/iris/iris.module';
import { IrisBaseChatbotComponent } from 'app/iris/base-chatbot/iris-base-chatbot.component';

@Component({
    selector: 'jhi-course-chatbot',
    templateUrl: './course-chatbot.component.html',
    styleUrl: './course-chatbot.component.scss',
    standalone: true,
    imports: [IrisModule, IrisModule, IrisBaseChatbotComponent],
})
export class CourseChatbotComponent implements OnChanges {
    courseId = input<number>();

    constructor(public chatService: IrisChatService) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.courseId) {
            this.chatService.switchTo(ChatServiceMode.COURSE, this.courseId());
        }
    }
}
