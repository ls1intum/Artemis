import { Component, OnInit } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { matchesRegexFully } from 'app/utils/regex.util';
import { faCheckCircle } from '@fortawesome/free-solid-svg-icons';
import { AlertService } from 'app/core/util/alert.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CoursePrerequisitesModalComponent } from 'app/overview/course-registration/course-prerequisites-modal.component';

@Component({
    selector: 'jhi-course-registration-selector',
    templateUrl: './course-registration.component.html',
})
export class CourseRegistrationComponent implements OnInit {
    coursesToSelect: Course[] = [];
    userIsAllowedToRegister = false;
    loading = false;

    // Icons
    faCheckCircle = faCheckCircle;

    constructor(
        private accountService: AccountService,
        private courseService: CourseManagementService,
        private profileService: ProfileService,
        private alertService: AlertService,
        private modalService: NgbModal,
    ) {}

    ngOnInit(): void {
        this.loadRegistrableCourses();
        this.accountService.identity().then((user) => {
            this.profileService.getProfileInfo().subscribe((profileInfo) => {
                if (profileInfo) {
                    this.userIsAllowedToRegister = matchesRegexFully(user!.login, profileInfo.allowedCourseRegistrationUsernamePattern);
                }
            });
        });
    }

    /**
     * Loads all course that are available for self-registration by the logged-in user
     */
    loadRegistrableCourses() {
        this.loading = true;
        this.courseService.findAllToRegister().subscribe((registerRes) => {
            this.coursesToSelect = registerRes.body!.sort((a, b) => (a.title ?? '').localeCompare(b.title ?? ''));
            this.loading = false;
        });
    }

    /**
     * Opens a modal with the prerequisites for the course
     * @param courseId The course id for which to show the prerequisites
     */
    showPrerequisites(courseId: number) {
        const modalRef = this.modalService.open(CoursePrerequisitesModalComponent, { size: 'xl' });
        modalRef.componentInstance.courseId = courseId;
    }

    /**
     * Register the logged-in user for the course
     * @param courseId The id of course to register the user for
     */
    registerForCourse(courseId: number) {
        this.courseService.registerForCourse(courseId).subscribe({
            next: () => {
                this.alertService.success('artemisApp.studentDashboard.register.registerSuccessful');
                this.coursesToSelect = this.coursesToSelect.filter((course) => course.id !== courseId);
            },
            error: (error: string) => {
                this.alertService.error(error);
            },
        });
    }
}
