import { Component, Input, OnChanges, OnInit, SimpleChanges, inject } from '@angular/core';
import { ChatServiceMode, IrisChatService } from 'app/iris/iris-chat.service';
import { IrisBaseChatbotComponent } from '../base-chatbot/iris-base-chatbot.component';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-course-chatbot',
    templateUrl: './course-chatbot.component.html',
    styleUrl: './course-chatbot.component.scss',
    imports: [IrisBaseChatbotComponent],
})
export class CourseChatbotComponent implements OnChanges, OnInit {
    chatService = inject(IrisChatService);
    private route = inject(ActivatedRoute);

    irisQuestion: string | undefined;

    @Input() courseId?: number;

    ngOnInit() {
        this.route.queryParams?.subscribe((params: any) => {
            if (params.irisQuestion) {
                this.irisQuestion = params.irisQuestion;
            }
        });
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.courseId) {
            this.chatService.switchTo(ChatServiceMode.COURSE, this.courseId);
        }
    }
}
