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
import { LtiPlatformConfiguration } from 'app/admin/lti-configuration/lti-configuration.model';
import { LtiConfigurationService } from 'app/admin/lti-configuration/lti-configuration.service';

@Component({
    selector: 'jhi-edit-course-lti-configuration',
    templateUrl: './edit-course-lti-configuration.component.html',
})
export class EditCourseLtiConfigurationComponent implements OnInit {
    course: Course;
    onlineCourseConfiguration: OnlineCourseConfiguration;
    onlineCourseConfigurationForm: FormGroup;
    ltiConfiguredPlatforms: LtiPlatformConfiguration[];

    isSaving = false;

    // Icons
    faBan = faBan;
    faSave = faSave;

    constructor(
        private route: ActivatedRoute,
        private courseService: CourseManagementService,
        private router: Router,
        private ltiConfigurationService: LtiConfigurationService,
    ) {}

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
            userPrefix: new FormControl(this.onlineCourseConfiguration?.userPrefix, { validators: [regexValidator(LOGIN_PATTERN)] }),
            requireExistingUser: new FormControl(this.onlineCourseConfiguration.requireExistingUser),
            ltiPlatformConfiguration: new FormControl(''),
        });

        this.getPreconfiguredPlatforms();
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

    /**
     * Gets the LTI 1.3 pre-configured platforms
     */
    getPreconfiguredPlatforms() {
        this.ltiConfigurationService.findAll().subscribe((configuredLtiPlatforms) => {
            if (configuredLtiPlatforms) {
                this.ltiConfiguredPlatforms = configuredLtiPlatforms;
            }
        });
    }

    setPlatform(platform: LtiPlatformConfiguration) {
        this.onlineCourseConfiguration.ltiPlatformConfiguration = platform;
        this.onlineCourseConfigurationForm.get('ltiPlatformConfiguration')!.setValue(platform);
    }

    getLtiPlatform(platform: LtiPlatformConfiguration) {
        const customName = platform.customName ? platform.customName : '';
        const originalUrl = platform.originalUrl ? platform.originalUrl : '';

        return customName + ' ' + originalUrl;
    }
}
