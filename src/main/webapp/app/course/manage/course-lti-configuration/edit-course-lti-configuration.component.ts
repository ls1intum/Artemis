import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { finalize } from 'rxjs';
import { OnlineCourseConfiguration } from 'app/entities/online-course-configuration.model';
import { FormControl, FormGroup } from '@angular/forms';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { regexValidator } from 'app/shared/form/shortname-validator.directive';
import { LOGIN_PATTERN } from 'app/shared/constants/input.constants';
import { CourseManagementService } from 'app/course/manage/course-management.service';

@Component({
    selector: 'jhi-edit-course-lti-configuration',
    templateUrl: './edit-course-lti-configuration.component.html',
})
export class EditCourseLtiConfigurationComponent implements OnInit {
    course: Course;
    onlineCourseConfiguration: OnlineCourseConfiguration;
    onlineCourseConfigurationForm: FormGroup;

    isSaving = false;

    // Icons
    faBan = faBan;
    faSave = faSave;

    constructor(private route: ActivatedRoute, private courseService: CourseManagementService, private router: Router) {}

    /**
     * Gets the configuration for the course encoded in the route and prepares the form
     */
    ngOnInit() {
        this.route.data.subscribe(({ course }) => {
            if (course) {
                this.course = course;
                this.onlineCourseConfiguration = course.onlineCourseConfiguration;
            }
        });

        this.onlineCourseConfigurationForm = new FormGroup({
            id: new FormControl(this.onlineCourseConfiguration.id),
            ltiKey: new FormControl(this.onlineCourseConfiguration.ltiKey),
            ltiSecret: new FormControl(this.onlineCourseConfiguration.ltiSecret),
            userPrefix: new FormControl(this.course.onlineCourseConfiguration?.userPrefix, { validators: [regexValidator(LOGIN_PATTERN)] }),
            registrationId: new FormControl(this.onlineCourseConfiguration.registrationId),
            clientId: new FormControl(this.onlineCourseConfiguration?.clientId),
            authorizationUri: new FormControl(this.onlineCourseConfiguration?.authorizationUri),
            tokenUri: new FormControl(this.onlineCourseConfiguration?.tokenUri),
            jwkSetUri: new FormControl(this.onlineCourseConfiguration?.jwkSetUri),
        });
    }

    /**
     * Save the changes to the online course configuration
     */
    save() {
        this.isSaving = true;
        const onlineCourseConfiguration = this.onlineCourseConfigurationForm.getRawValue();
        this.courseService
            .updateOnlineCourseConfiguration(this.course.id!, onlineCourseConfiguration)
            .pipe(
                finalize(() => {
                    this.isSaving = false;
                }),
            )
            .subscribe({
                next: () => this.onSaveSuccess(),
            });
    }

    /**
     * Action on successful online course configuration or edit
     */
    private onSaveSuccess() {
        this.isSaving = false;
        this.navigateToLtiConfigurationPage();
    }

    /**
     * Gets the user prefix
     */
    get userPrefix() {
        return this.onlineCourseConfigurationForm.get('userPrefix')!;
    }

    /**
     * Returns to the lti configuration page
     */
    navigateToLtiConfigurationPage() {
        this.router.navigate(['course-management', this.course.id!.toString(), 'lti-configuration']);
    }
}
