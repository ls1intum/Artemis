import { Component, OnInit } from '@angular/core';
import { CourseManagementService } from '../course/manage/course-management.service';
import { SessionStorageService } from 'ngx-webstorage';
import { OnlineCourseDtoModel } from 'app/lti/online-course-dto.model';
import { AlertService } from 'app/core/util/alert.service';
import { LtiCourseCardComponent } from './lti-course-card.component';
import { TranslateDirective } from '../shared/language/translate.directive';

@Component({
    selector: 'jhi-lti-courses-overview',
    templateUrl: './lti13-select-course.component.html',
    standalone: true,
    imports: [LtiCourseCardComponent, TranslateDirective],
})
export class LtiCoursesComponent implements OnInit {
    public courses: OnlineCourseDtoModel[];
    constructor(
        private courseService: CourseManagementService,
        private sessionStorageService: SessionStorageService,
        private alertService: AlertService,
    ) {}

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
