import { Component, OnInit } from '@angular/core';
import { CourseManagementService } from '../course/manage/course-management.service';
import { OnlineCourseDtoModel } from 'app/lti/online-course-dto.model';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-lti-courses-overview',
    templateUrl: './lti13-select-course.component.html',
})
export class LtiCoursesComponent implements OnInit {
    public courses: OnlineCourseDtoModel[];
    constructor(
        private courseService: CourseManagementService,
        private alertService: AlertService,
    ) {}

    async ngOnInit() {
        this.loadAndFilterCourses();
    }

    loadAndFilterCourses() {
        this.courseService.findAllOnlineCoursesWithRegistrationId().subscribe({
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
