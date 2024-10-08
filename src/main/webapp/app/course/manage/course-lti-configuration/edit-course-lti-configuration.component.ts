import { Component, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
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
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { combineLatest } from 'rxjs';

@Component({
    selector: 'jhi-edit-course-lti-configuration',
    templateUrl: './edit-course-lti-configuration.component.html',
})
export class EditCourseLtiConfigurationComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private courseService = inject(CourseManagementService);
    private router = inject(Router);
    private ltiConfigurationService = inject(LtiConfigurationService);

    @ViewChild('scrollableContent') scrollableContent: ElementRef;

    course: Course;
    onlineCourseConfiguration: OnlineCourseConfiguration;
    onlineCourseConfigurationForm: FormGroup;
    ltiConfiguredPlatforms: LtiPlatformConfiguration[] = [];

    page = 1;
    itemsPerPage = ITEMS_PER_PAGE;
    totalItems = 0;

    isSaving = false;
    loading = false;

    // Icons
    faBan = faBan;
    faSave = faSave;

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
            userPrefix: new FormControl(this.onlineCourseConfiguration?.userPrefix, { validators: [regexValidator(LOGIN_PATTERN)] }),
            requireExistingUser: new FormControl(this.onlineCourseConfiguration.requireExistingUser),
            ltiPlatformConfiguration: new FormControl(''),
        });

        this.loadInitialPlatforms();
    }

    loadInitialPlatforms() {
        combineLatest({ data: this.route.data, params: this.route.queryParamMap }).subscribe(({ params }) => {
            const page = params.get('page');
            this.page = page !== null ? +page : 1;
            this.loadData();
        });
    }

    loadData(): void {
        this.ltiConfigurationService
            .query({
                page: this.page - 1,
                size: this.itemsPerPage,
                sort: ['id', 'asc'],
            })
            .subscribe((res: HttpResponse<LtiPlatformConfiguration[]>) => this.onSuccess(res.body, res.headers));
    }

    transition(): void {
        this.router.navigate(['/admin/lti-configuration'], {
            queryParams: {
                page: this.page,
                sort: ['id', 'asc'],
            },
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

    private onSuccess(platforms: LtiPlatformConfiguration[] | null, headers: HttpHeaders): void {
        this.totalItems = Number(headers.get('X-Total-Count'));
        this.ltiConfiguredPlatforms = platforms || [];
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

    setPlatform(platform: LtiPlatformConfiguration) {
        this.onlineCourseConfiguration.ltiPlatformConfiguration = platform;
        this.onlineCourseConfigurationForm.get('ltiPlatformConfiguration')?.setValue(platform);
    }

    getLtiPlatform(platform: LtiPlatformConfiguration) {
        const customName = platform.customName ? platform.customName : '';
        const originalUrl = platform.originalUrl ? platform.originalUrl : '';

        return customName + ' ' + originalUrl;
    }
}
