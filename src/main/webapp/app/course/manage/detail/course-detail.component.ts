import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { MODULE_FEATURE_ATHENA, MODULE_FEATURE_ATLAS, MODULE_FEATURE_HYPERION, MODULE_FEATURE_IRIS, PROFILE_LTI } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Subscription, catchError, combineLatest, of, startWith, switchMap } from 'rxjs';
import { Course } from 'app/course/shared/entities/course.model';
import { CourseManagementService } from '../services/course-management.service';
import { CourseManagementDetailViewDto } from 'app/course/shared/entities/course-management-detail-view-dto.model';
import { onError } from 'app/foundation/util/global.utils';
import { AlertService } from 'app/foundation/service/alert.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import {
    faBolt,
    faChartBar,
    faClipboard,
    faEye,
    faFileImport,
    faFlag,
    faGraduationCap,
    faListAlt,
    faQuestion,
    faTable,
    faTimes,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { AccountService } from 'app/core/auth/account.service';
import { DetailOverviewListComponent, DetailOverviewSection, DetailType } from 'app/shared-ui/detail-overview-list/detail-overview-list.component';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { Detail } from 'app/shared-ui/detail-overview-list/detail.model';
import { CourseDetailDoughnutChartComponent } from './course-detail-doughnut-chart.component';
import { CourseDetailLineChartComponent } from './course-detail-line-chart.component';
import { QuickActionsComponent } from 'app/course/manage/quick-actions/quick-actions.component';
import { ControlCenterComponent } from 'app/course/manage/control-center/control-center.component';
import { IrisAssessmentAttentionCenterComponent } from 'app/course/manage/iris-assessment-attention-center/iris-assessment-attention-center.component';
import { OnboardingExploreComponent } from 'app/course/manage/onboarding/pages/onboarding-explore.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

export enum DoughnutChartType {
    ASSESSMENT = 'ASSESSMENT',
    COMPLAINTS = 'COMPLAINTS',
    FEEDBACK = 'FEEDBACK',
    AVERAGE_COURSE_SCORE = 'AVERAGE_COURSE_SCORE',
    AVERAGE_EXERCISE_SCORE = 'AVERAGE_EXERCISE_SCORE',
    PARTICIPATIONS = 'PARTICIPATIONS',
    QUESTIONS = 'QUESTIONS',
    CURRENT_LLM_COST = 'LLM_COST',
}

@Component({
    selector: 'jhi-course-detail',
    templateUrl: './course-detail.component.html',
    styleUrls: ['./course-detail.component.scss'],
    imports: [
        CourseDetailDoughnutChartComponent,
        CourseDetailLineChartComponent,
        DetailOverviewListComponent,
        QuickActionsComponent,
        ControlCenterComponent,
        IrisAssessmentAttentionCenterComponent,
        OnboardingExploreComponent,
        FaIconComponent,
        TranslateDirective,
    ],
})
export class CourseDetailComponent implements OnInit, OnDestroy {
    protected readonly DoughnutChartType = DoughnutChartType;
    protected readonly FeatureToggle = FeatureToggle;

    protected readonly faTimes = faTimes;
    protected readonly faEye = faEye;
    protected readonly faWrench = faWrench;
    protected readonly faTable = faTable;
    protected readonly faFlag = faFlag;
    protected readonly faListAlt = faListAlt;
    protected readonly faChartBar = faChartBar;
    protected readonly faClipboard = faClipboard;
    protected readonly faGraduationCap = faGraduationCap;
    protected readonly faQuestion = faQuestion;
    protected readonly faBolt = faBolt;
    protected readonly faFileImport = faFileImport;

    private eventManager = inject(EventManager);
    private courseManagementService = inject(CourseManagementService);
    private organizationService = inject(OrganizationManagementService);
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private readonly profileService = inject(ProfileService);
    private accountService = inject(AccountService);
    private readonly irisSettingsService = inject(IrisSettingsService);
    private markdownService = inject(ArtemisMarkdownService);
    private readonly featureToggleService = inject(FeatureToggleService);

    readonly courseDTO = signal<CourseManagementDetailViewDto | undefined>(undefined);
    readonly course = signal<Course | undefined>(undefined);

    readonly courseDetailSections = signal<DetailOverviewSection[]>([]);

    readonly messagingEnabled = signal(false);
    readonly communicationEnabled = signal(false);

    readonly irisEnabled = signal(this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS));

    // Keep the settings reactive so the assessment attention box updates after changes in the control center.
    private readonly irisSettings = toSignal(
        combineLatest([toObservable(this.course), this.irisSettingsService.refresh$.pipe(startWith(undefined))]).pipe(
            switchMap(([course]) => {
                if (!this.irisEnabled() || !course?.isAtLeastInstructor || course.id === undefined) {
                    return of(undefined);
                }

                return this.irisSettingsService.getCourseSettingsWithRateLimit(course.id).pipe(
                    catchError((error: HttpErrorResponse) => {
                        onError(this.alertService, error);
                        return of(undefined);
                    }),
                );
            }),
        ),
        { initialValue: undefined },
    );

    private readonly askUserModeFeatureEnabled = toSignal(this.featureToggleService.getFeatureToggleActive(FeatureToggle.AskUserMode), { initialValue: false });

    readonly irisChatEnabled = computed(() => this.irisSettings()?.settings?.enabled ?? false);
    readonly irisAskUserModeEnabled = computed(() => this.askUserModeFeatureEnabled() && (this.irisSettings()?.settings?.askUserModeEnabled ?? false));

    readonly ltiEnabled = signal(false);
    readonly isAthenaEnabled = signal(false);
    readonly isHyperionEnabled = signal(false);
    readonly isAtlasEnabled = signal(false);
    readonly fromOnboarding = signal(false);

    readonly isAdmin = signal(false);

    private eventSubscription?: Subscription;
    paramSub?: Subscription;

    /**
     * On init load the course information and subscribe to listen for changes in courses.
     */
    async ngOnInit() {
        this.ltiEnabled.set(this.profileService.isProfileActive(PROFILE_LTI));
        this.isAthenaEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATHENA));
        this.isHyperionEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION));
        this.isAtlasEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS));
        this.fromOnboarding.set(this.route.snapshot.queryParamMap.get('fromOnboarding') === 'true');

        this.route.data.subscribe(({ course }) => {
            if (course) {
                this.setCourse(course);
            }
            this.isAdmin.set(this.accountService.isAdmin());
            this.getCourseDetailSections();
        });
        this.paramSub = this.route.params.subscribe((params) => {
            const courseId = Number(params['courseId']);
            if (!Number.isNaN(courseId)) {
                this.fetchCourseStatistics(courseId);
                this.registerChangeInCourses(courseId);
            }
        });
    }

    getGeneralDetailSection(): DetailOverviewSection {
        const course = this.requireCourse();
        const generalDetails: Detail[] = [
            { type: DetailType.Text, title: 'artemisApp.course.title', data: { text: course.title } },
            { type: DetailType.Text, title: 'artemisApp.course.shortName', data: { text: course.shortName } },
            { type: DetailType.Date, title: 'artemisApp.course.startDate', data: { date: course.startDate } },
            { type: DetailType.Date, title: 'artemisApp.course.endDate', data: { date: course.endDate } },
            { type: DetailType.Text, title: 'artemisApp.course.semester', data: { text: course.semester } },
        ];

        if (course.organizations?.length) {
            // insert detail after shortName
            generalDetails.splice(2, 0, {
                type: DetailType.Text,
                title: 'artemisApp.course.organizations',
                data: { text: course.organizations.map((orga) => orga.name).join(', ') },
            });
        }
        return {
            headline: 'artemisApp.course.detail.sections.general',
            details: generalDetails,
        };
    }

    getComplaintsDetails(): Detail[] {
        const course = this.requireCourse();
        if (course.complaintsEnabled) {
            return [
                {
                    type: DetailType.Text,
                    title: 'artemisApp.course.maxComplaints.title',
                    data: { text: course.maxComplaints },
                },
                {
                    type: DetailType.Text,
                    title: 'artemisApp.course.maxTeamComplaints.title',
                    data: { text: course.maxTeamComplaints },
                },
                {
                    type: DetailType.Text,
                    title: 'artemisApp.course.maxComplaintTimeDays.title',
                    data: { text: course.maxComplaintTimeDays },
                },
                {
                    type: DetailType.Text,
                    title: 'artemisApp.course.maxComplaintTextLimit.title',
                    data: { text: course.maxComplaintTextLimit },
                },
                {
                    type: DetailType.Text,
                    title: 'artemisApp.course.maxComplaintResponseTextLimit.title',
                    data: { text: course.maxComplaintResponseTextLimit },
                },
            ];
        }
        return [];
    }

    getAthenaDetails(): Detail[] {
        const course = this.requireCourse();
        const athenaDetails: Detail[] = [];
        if (this.isAthenaEnabled()) {
            athenaDetails.push({
                type: DetailType.Boolean,
                title: 'artemisApp.course.restrictedAthenaModulesAccess.label',
                data: { boolean: course.restrictedAthenaModulesAccess },
            });
        }
        return athenaDetails;
    }

    getModeDetailSection(): DetailOverviewSection {
        const course = this.requireCourse();
        const complaintsDetails = this.getComplaintsDetails();
        const athenaDetails = this.getAthenaDetails();

        const details: Detail[] = [
            {
                type: DetailType.Text,
                title: 'artemisApp.course.maxPoints.title',
                titleHelpText: 'artemisApp.course.maxPoints.info',
                data: { text: course.maxPoints },
            },
            {
                type: DetailType.Text,
                title: 'artemisApp.course.accuracyOfScores.title',
                titleHelpText: 'artemisApp.course.accuracyOfScores.info',
                data: { text: course.accuracyOfScores },
            },
            {
                type: DetailType.Text,
                title: 'artemisApp.course.defaultProgrammingLanguage',
                data: { text: course.defaultProgrammingLanguage },
            },
            {
                type: DetailType.Boolean,
                title: 'artemisApp.course.testCourse.title',
                data: { boolean: course.testCourse },
            },
            ...complaintsDetails,
            ...athenaDetails,
        ];

        // inserting optional details in reversed order, so that no index calculation is needed
        if (course.requestMoreFeedbackEnabled) {
            // insert detail after the complaintDetails
            details.splice(4 + complaintsDetails.length, 0, {
                type: DetailType.Text,
                title: 'artemisApp.course.maxRequestMoreFeedbackTimeDays.title',
                data: { text: course.maxRequestMoreFeedbackTimeDays },
            });
        }

        details.splice(4, 0, {
            type: DetailType.Text,
            title: 'artemisApp.forms.configurationForm.timeZoneInput.label',
            data: { text: course.timeZone },
        });

        if (this.ltiEnabled()) {
            // insert lti detail after testCourse detail
            details.splice(4, 0, {
                type: DetailType.Boolean,
                title: 'artemisApp.course.onlineCourse.title',
                data: { boolean: course.onlineCourse },
            });
        }

        return {
            headline: 'artemisApp.course.detail.sections.mode',
            details: details,
        };
    }

    getEnrollmentDetailSection(): DetailOverviewSection {
        const course = this.requireCourse();
        const enrollmentDetails: Detail[] = [
            { type: DetailType.Boolean, title: 'artemisApp.course.enrollmentEnabled.title', data: { boolean: course.enrollmentEnabled } },
            { type: DetailType.Boolean, title: 'artemisApp.course.unenrollmentEnabled.title', data: { boolean: course.unenrollmentEnabled } },
        ];

        if (course.enrollmentEnabled) {
            // insert enrollment details after enrollmentEnabled detail
            enrollmentDetails.splice(
                1,
                0,
                { type: DetailType.Date, title: 'artemisApp.course.enrollmentStartDate', data: { date: course.enrollmentStartDate } },
                { type: DetailType.Date, title: 'artemisApp.course.enrollmentEndDate', data: { date: course.enrollmentEndDate } },
                {
                    type: DetailType.Markdown,
                    title: 'artemisApp.course.enrollmentConfirmationMessage',
                    data: { innerHtml: this.markdownService.safeHtmlForMarkdown(course.enrollmentConfirmationMessage) },
                },
            );
        }

        if (course.unenrollmentEnabled) {
            // insert unenrollment detail after unenrollmentEnabled detail
            enrollmentDetails.push({ type: DetailType.Date, title: 'artemisApp.course.unenrollmentEndDate', data: { date: course.unenrollmentEndDate } });
        }
        return {
            headline: 'artemisApp.course.detail.sections.enrollment',
            details: enrollmentDetails,
        };
    }

    getMessagingDetailSection(): DetailOverviewSection {
        const course = this.requireCourse();
        return {
            headline: 'artemisApp.course.detail.sections.messaging',
            details: [
                { type: DetailType.Boolean, title: 'artemisApp.course.courseCommunicationSetting.communicationEnabled.label', data: { boolean: this.communicationEnabled() } },
                { type: DetailType.Boolean, title: 'artemisApp.course.courseCommunicationSetting.messagingEnabled.label', data: { boolean: this.messagingEnabled() } },
                {
                    type: DetailType.Markdown,
                    title: 'artemisApp.course.courseCommunicationSetting.messagingEnabled.codeOfConduct',
                    data: { innerHtml: this.markdownService.safeHtmlForMarkdown(course.courseInformationSharingMessagingCodeOfConduct) },
                },
            ],
        };
    }

    getCourseDetailSections() {
        if (!this.course()) {
            this.courseDetailSections.set([]);
            return;
        }
        const generalSection = this.getGeneralDetailSection();
        const modeSection = this.getModeDetailSection();
        const enrollmentSection = this.getEnrollmentDetailSection();
        const messagingSection = this.getMessagingDetailSection();
        this.courseDetailSections.set([generalSection, modeSection, enrollmentSection, messagingSection]);
    }

    /**
     * Subscribe to changes in courses and reload the course after a change.
     */
    registerChangeInCourses(courseId: number) {
        this.eventSubscription = this.eventManager.subscribe('courseListModification', () => {
            this.courseManagementService.find(courseId).subscribe((courseResponse) => {
                this.setCourse(courseResponse.body!);
                this.getCourseDetailSections();
            });
            this.fetchCourseStatistics(courseId);
        });
    }

    /**
     * On destroy unsubscribe all subscriptions.
     */
    ngOnDestroy() {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
        if (this.eventSubscription) {
            this.eventManager.destroy(this.eventSubscription);
        }
    }

    /**
     * fetch the course specific statistics separately because it takes quite long for larger courses
     */
    private fetchCourseStatistics(courseId: number) {
        this.courseManagementService.getCourseStatisticsForDetailView(courseId).subscribe({
            next: (courseResponse: HttpResponse<CourseManagementDetailViewDto>) => {
                this.courseDTO.set(courseResponse.body!);
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    private fetchOrganizations(courseId: number) {
        this.organizationService.getOrganizationsByCourse(courseId).subscribe((organizations) => {
            const course = this.course();
            if (!course) {
                return;
            }
            this.course.set({ ...course, organizations });
            this.getCourseDetailSections();
        });
    }

    /**
     * Sets the given course as the current course, derives the messaging/communication flags from it,
     * and triggers loading its organizations.
     * @param course The course to set as the current course
     */
    private setCourse(course: Course) {
        this.course.set(course);
        this.messagingEnabled.set(!!course.courseInformationSharingConfiguration?.includes('MESSAGING'));
        this.communicationEnabled.set(!!course.courseInformationSharingConfiguration?.includes('COMMUNICATION'));
        if (course.id !== undefined) {
            this.fetchOrganizations(course.id);
        }
    }

    /**
     * Returns the currently loaded course, throwing if it has not been loaded yet.
     * @returns The currently loaded course
     */
    private requireCourse(): Course {
        const course = this.course();
        if (!course) {
            throw new Error('Course detail sections cannot be created before the course is loaded.');
        }
        return course;
    }
}
