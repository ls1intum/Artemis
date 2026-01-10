import { Component, ElementRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Course } from 'app/core/course/shared/entities/course.model';
import { combineLatest, finalize } from 'rxjs';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { OnlineCourseConfiguration } from 'app/lti/shared/entities/online-course-configuration.model';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { regexValidator } from 'app/shared/form/shortname-validator.directive';
import { LOGIN_PATTERN } from 'app/shared/constants/input.constants';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { LtiPlatformConfiguration } from 'app/lti/shared/entities/lti-configuration.model';
import { LtiConfigurationService } from 'app/core/admin/lti-configuration/lti-configuration.service';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbDropdown, NgbDropdownButtonItem, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbPagination, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { KeyValuePipe } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';

@Component({
    selector: 'jhi-edit-course-lti-configuration',
    templateUrl: './edit-course-lti-configuration.component.html',
    imports: [
        FormsModule,
        ReactiveFormsModule,
        TranslateDirective,
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        NgbDropdownButtonItem,
        NgbDropdownItem,
        NgbTooltip,
        NgbPagination,
        FaIconComponent,
        KeyValuePipe,
        ArtemisTranslatePipe,
        RemoveKeysPipe,
        ItemCountComponent,
        HelpIconComponent,
        // NOTE: this is actually used in the html template, otherwise *jhiHasAnyAuthority would not work
        HasAnyAuthorityDirective,
    ],
})
export class EditCourseLtiConfigurationComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private courseService = inject(CourseManagementService);
    private router = inject(Router);
    private ltiConfigurationService = inject(LtiConfigurationService);

    readonly scrollableContent = viewChild.required<ElementRef>('scrollableContent');

    course: Course;
    onlineCourseConfiguration: OnlineCourseConfiguration;
    onlineCourseConfigurationForm: FormGroup;
    readonly ltiConfiguredPlatforms = signal<LtiPlatformConfiguration[]>([]);

    readonly page = signal(1);
    readonly itemsPerPage = signal(ITEMS_PER_PAGE);
    readonly totalItems = signal(0);

    readonly isSaving = signal(false);
    readonly loading = signal(false);

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
            const pageParam = params.get('page');
            this.page.set(pageParam !== null ? +pageParam : 1);
            this.loadData();
        });
    }

    loadData(): void {
        this.ltiConfigurationService
            .query({
                page: this.page() - 1,
                size: this.itemsPerPage(),
                sort: ['id', 'asc'],
            })
            .subscribe((res: HttpResponse<LtiPlatformConfiguration[]>) => this.onSuccess(res.body, res.headers));
    }

    transition(): void {
        this.router.navigate(['/admin/lti-configuration'], {
            queryParams: {
                page: this.page(),
                sort: ['id', 'asc'],
            },
        });
    }

    /**
     * Save the changes to the online course configuration
     */
    save() {
        this.isSaving.set(true);
        const onlineCourseConfiguration = this.onlineCourseConfigurationForm.getRawValue();
        this.courseService
            .updateOnlineCourseConfiguration(this.course.id!, onlineCourseConfiguration)
            .pipe(
                finalize(() => {
                    this.isSaving.set(false);
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
        this.isSaving.set(false);
        this.navigateToLtiConfigurationPage();
    }

    private onSuccess(platforms: LtiPlatformConfiguration[] | null, headers: HttpHeaders): void {
        this.totalItems.set(Number(headers.get('X-Total-Count')));
        this.ltiConfiguredPlatforms.set(platforms || []);
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
