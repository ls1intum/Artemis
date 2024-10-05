import { Component, Input, OnInit, inject } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { Course } from 'app/entities/course.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';

@Component({
    selector: 'jhi-course-conversations-code-of-conduct',
    templateUrl: './course-conversations-code-of-conduct.component.html',
})
export class CourseConversationsCodeOfConductComponent implements OnInit {
    private alertService = inject(AlertService);
    private conversationService = inject(ConversationService);

    @Input()
    course: Course;

    responsibleContacts: User[] = [];

    ngOnInit() {
        if (this.course.id) {
            this.conversationService.getResponsibleUsersForCodeOfConduct(this.course.id).subscribe({
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
