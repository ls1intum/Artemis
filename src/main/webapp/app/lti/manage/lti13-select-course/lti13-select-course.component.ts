import { Component, OnInit, inject } from '@angular/core';
import { CourseManagementService } from '../../../core/course/manage/services/course-management.service';
import { SessionStorageService } from 'ngx-webstorage';
import { OnlineCourseDtoModel } from 'app/lti/shared/entities/online-course-dto.model';
import { AlertService } from 'app/shared/service/alert.service';
import { LtiCourseCardComponent } from '../lti-course-card/lti-course-card.component';
import { TranslateDirective } from '../../../shared/language/translate.directive';

@Component({
    selector: 'jhi-lti-courses-overview',
    templateUrl: './lti13-select-course.component.html',
    imports: [LtiCourseCardComponent, TranslateDirective],
})
export class LtiCoursesComponent implements OnInit {
    private courseService = inject(CourseManagementService);
    private sessionStorageService = inject(SessionStorageService);
    private alertService = inject(AlertService);

    public courses: OnlineCourseDtoModel[];

    async ngOnInit() {
        this.loadAndFilterCourses();
    }

    loadAndFilterCourses() {
        const clientId = this.sessionStorageService.retrieve('clientRegistrationId');
        this.courseService.findAllOnlineCoursesWithRegistrationId(clientId).subscribe({
            next: (courseResponse: OnlineCourseDtoModel[]) => {
                this.courses = courseResponse;
            },
            error: (error) => {
                this.alertService.error('error.unexpectedError', {
                    error: error.message,
                });
            },
        });
    }
}
