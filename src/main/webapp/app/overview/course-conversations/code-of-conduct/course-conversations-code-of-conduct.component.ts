import { Component, Input, OnInit } from '@angular/core';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-course-conversations-code-of-conduct',
    templateUrl: './course-conversations-code-of-conduct.component.html',
})
export class CourseConversationsCodeOfConductComponent implements OnInit {
    @Input()
    course: Course;

    responsibleContacts: UserPublicInfoDTO[] = [];

    constructor(
        private alertService: AlertService,
        private courseManagementService: CourseManagementService,
    ) {}

    ngOnInit() {
        if (this.course.id) {
            this.courseManagementService.searchUsers(this.course.id, '', ['instructors']).subscribe({
                next: (res: HttpResponse<UserPublicInfoDTO[]>) => {
                    if (res.body) {
                        this.responsibleContacts = res.body;
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
        }
    }
}
