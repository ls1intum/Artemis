import { Component, OnInit } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { faCheckCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-course-registration-selector',
    templateUrl: './course-registration.component.html',
})
export class CourseRegistrationComponent implements OnInit {
    coursesToSelect: Course[] = [];
    loading = false;

    // Icons
    faCheckCircle = faCheckCircle;

    constructor(
        private accountService: AccountService,
        private courseService: CourseManagementService,
    ) {}

    ngOnInit(): void {
        this.loadRegistrableCourses();
    }

    /**
     * Loads all course that are available for self-registration by the logged-in user
     */
    loadRegistrableCourses() {
        this.loading = true;
        this.courseService.findAllForRegistration().subscribe((res) => {
            this.coursesToSelect = res.body!.sort((a, b) => (a.title ?? '').localeCompare(b.title ?? ''));
            this.loading = false;
        });
    }

    /**
     * Removes a course from the list of courses that the user can register for
     * after the user has registered for the course
     * @param courseId the id of the course that the user has registered for
     */
    removeCourseFromList(courseId: number) {
        this.coursesToSelect = this.coursesToSelect.filter((course) => course.id !== courseId);
    }
}
