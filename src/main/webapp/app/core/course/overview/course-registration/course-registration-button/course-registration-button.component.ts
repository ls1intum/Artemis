import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { matchesRegexFully } from 'app/shared/util/regex.util';
import { AlertService } from 'app/shared/service/alert.service';
import { ConfirmAutofocusButtonComponent } from 'app/shared/components/buttons/confirm-autofocus-button/confirm-autofocus-button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-course-registration-button',
    templateUrl: './course-registration-button.component.html',
    imports: [ConfirmAutofocusButtonComponent, TranslateDirective],
})
export class CourseRegistrationButtonComponent implements OnInit {
    private accountService = inject(AccountService);
    private courseService = inject(CourseManagementService);
    private profileService = inject(ProfileService);
    private alertService = inject(AlertService);

    @Input() course: Course;
    @Output() onRegistration = new EventEmitter<void>();

    userIsAllowedToRegister = false;
    loading = false;

    loadUserIsAllowedToRegister() {
        this.loading = true;
        this.accountService.identity().then((user) => {
            const profileInfo = this.profileService.getProfileInfo();
            if (user?.login) {
                this.userIsAllowedToRegister = matchesRegexFully(user.login, profileInfo.allowedCourseRegistrationUsernamePattern);
            }
            this.loading = false;
        });
    }

    ngOnInit(): void {
        this.loadUserIsAllowedToRegister();
    }

    /**
     * Enroll the logged-in user for the course
     * @param courseId The id of course to enroll the user in
     */
    registerForCourse(courseId: number) {
        this.courseService.registerForCourse(courseId).subscribe({
            next: () => {
                this.alertService.success('artemisApp.studentDashboard.enroll.enrollSuccessful');
                // TODO: The 'emit' function requires a mandatory void argument
                this.onRegistration.emit();
            },
            error: (error: string) => {
                this.alertService.error(error);
            },
        });
    }
}
