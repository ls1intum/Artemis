import { Component, OnInit } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { AlertService } from 'app/core/util/alert.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-course-registration-detail-selector',
    templateUrl: './course-registration-detail.component.html',
})
export class CourseRegistrationDetailComponent implements OnInit {
    userIsAllowedToRegister = false;
    loading = false;
    course: Course | null = null;
    courseLoading = true;

    constructor(
        private accountService: AccountService,
        private courseService: CourseManagementService,
        private profileService: ProfileService,
        private alertService: AlertService,
        private modalService: NgbModal,
    ) {}

    ngOnInit(): void {
        console.log('course registration detail init');
        this.courseService.find(1).subscribe((courseResponse) => {
            this.course = courseResponse.body!;
            this.courseLoading = false;
        });
    }
}
