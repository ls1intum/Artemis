import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
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
import { fromEvent } from 'rxjs';
import { debounceTime, distinctUntilChanged, filter, tap } from 'rxjs/operators';

@Component({
    selector: 'jhi-edit-course-lti-configuration',
    templateUrl: './edit-course-lti-configuration.component.html',
})
export class EditCourseLtiConfigurationComponent implements OnInit {
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

    constructor(
        private route: ActivatedRoute,
        private courseService: CourseManagementService,
        private router: Router,
        private ltiConfigurationService: LtiConfigurationService,
    ) {}

    handleScroll = (): void => {
        const target = this.scrollableContent.nativeElement;
        if (target.scrollTop + target.clientHeight >= target.scrollHeight) {
            this.loadMorePlatforms();
        }
    };

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
        this.setupScrollListener();
    }

    loadInitialPlatforms() {
        this.loading = true;
        this.ltiConfigurationService
            .query({
                page: 0,
                size: this.itemsPerPage,
                sort: ['id', 'asc'],
            })
            .subscribe({
                next: (res: HttpResponse<LtiPlatformConfiguration[]>) => {
                    if (res.body) {
                        this.ltiConfiguredPlatforms = res.body;
                        this.totalItems = Number(res.headers.get('X-Total-Count'));
                    }
                    this.loading = false;
                },
                error: (err) => {
                    console.error('Failed to load platforms:', err);
                    this.loading = false;
                },
            });
    }

    setupScrollListener() {
        fromEvent(window, 'scroll')
            .pipe(
                debounceTime(100),
                filter(() => window.scrollY + window.innerHeight >= document.documentElement.scrollHeight),
                distinctUntilChanged(),
                tap(() => this.loadMorePlatforms()),
            )
            .subscribe();
    }

    loadMorePlatforms() {
        if (this.ltiConfiguredPlatforms.length < this.totalItems) {
            this.page++;
            this.ltiConfigurationService
                .query({
                    page: this.page - 1,
                    size: this.itemsPerPage,
                    sort: ['id', 'asc'],
                })
                .subscribe((res: HttpResponse<LtiPlatformConfiguration[]>) => {
                    this.ltiConfiguredPlatforms = this.ltiConfiguredPlatforms.concat(res.body || []);
                });
        }
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
        this.ltiConfigurationService
            .query({
                page: this.page - 1,
                size: this.itemsPerPage,
                sort: ['id', 'asc'],
            })
            .subscribe((res: HttpResponse<LtiPlatformConfiguration[]>) => this.onSuccess(res.body, res.headers));
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

    private onSuccess(platforms: LtiPlatformConfiguration[] | null, headers: HttpHeaders): void {
        this.totalItems = Number(headers.get('X-Total-Count'));
        this.ltiConfiguredPlatforms = platforms || [];
    }
}
