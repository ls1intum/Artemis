import { Component, OnInit, inject, input } from '@angular/core';
import { User } from 'app/account/user/user.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/foundation/util/global.utils';
import { AlertService } from 'app/foundation/service/alert.service';
import { Course } from 'app/course/shared/entities/course.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HtmlForMarkdownPipe } from 'app/foundation/pipes/html-for-markdown.pipe';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';

@Component({
    selector: 'jhi-course-conversations-code-of-conduct',
    templateUrl: './course-conversations-code-of-conduct.component.html',
    imports: [TranslateDirective, HtmlForMarkdownPipe],
})
export class CourseConversationsCodeOfConductComponent implements OnInit {
    private alertService = inject(AlertService);
    private conversationService = inject(ConversationService);

    course = input.required<Course>();

    responsibleContacts: User[] = [];

    ngOnInit() {
        if (this.course().id) {
            this.conversationService.getResponsibleUsersForCodeOfConduct(this.course().id!).subscribe({
                next: (res: HttpResponse<User[]>) => {
                    if (res.body) {
                        this.responsibleContacts = res.body;
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
        }
    }
}
