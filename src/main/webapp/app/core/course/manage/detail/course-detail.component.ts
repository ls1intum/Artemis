import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { MODULE_FEATURE_HYPERION, MODULE_FEATURE_LTI, PROFILE_ATHENA, PROFILE_IRIS } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Subscription, firstValueFrom } from 'rxjs';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CourseManagementService } from '../services/course-management.service';
import { CourseManagementDetailViewDto } from 'app/core/course/shared/entities/course-management-detail-view-dto.model';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/shared/service/alert.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { faChartBar, faClipboard, faEye, faFlag, faGraduationCap, faListAlt, faQuestion, faTable, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { AccountService } from 'app/core/auth/account.service';
import { DetailOverviewListComponent, DetailOverviewSection, DetailType } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { Detail } from 'app/shared/detail-overview-list/detail.model';
import { CourseDetailDoughnutChartComponent } from './course-detail-doughnut-chart.component';
import { CourseDetailLineChartComponent } from './course-detail-line-chart.component';
import { QuickActionsComponent } from 'app/core/course/manage/quick-actions/quick-actions.component';
import { ControlCenterComponent } from 'app/core/course/manage/control-center/control-center.component';

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
    imports: [CourseDetailDoughnutChartComponent, CourseDetailLineChartComponent, DetailOverviewListComponent, QuickActionsComponent, ControlCenterComponent],
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

    private eventManager = inject(EventManager);
    private courseManagementService = inject(CourseManagementService);
    private organizationService = inject(OrganizationManagementService);
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private profileService = inject(ProfileService);
    private accountService = inject(AccountService);
    private irisSettingsService = inject(IrisSettingsService);
    private markdownService = inject(ArtemisMarkdownService);

    readonly courseDTO = signal<CourseManagementDetailViewDto | undefined>(undefined);
    readonly course = signal<Course | undefined>(undefined);

    readonly courseDetailSections = signal<DetailOverviewSection[]>([]);

    readonly messagingEnabled = signal(false);
    readonly communicationEnabled = signal(false);
    readonly irisEnabled = signal(false);
    readonly irisChatEnabled = signal(false);
    readonly ltiEnabled = signal(false);
    readonly isAthenaEnabled = signal(false);
    readonly isHyperionEnabled = signal(false);

    readonly isAdmin = signal(false);

    private eventSubscription: Subscription;
    private paramSub: Subscription;

    /**
     * On init load the course information and subscribe to listen for changes in courses.
     */
    async ngOnInit() {
        this.ltiEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_LTI));
        this.isAthenaEnabled.set(this.profileService.isProfileActive(PROFILE_ATHENA));
        this.irisEnabled.set(this.profileService.isProfileActive(PROFILE_IRIS));
        this.isHyperionEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION));

        this.route.data.subscribe(({ course }) => {
            if (course) {
                this.course.set(course);
                this.messagingEnabled.set(!!course.courseInformationSharingConfiguration?.includes('MESSAGING'));
                this.communicationEnabled.set(!!course.courseInformationSharingConfiguration?.includes('COMMUNICATION'));
                this.fetchOrganizations(course.id);
            }
            this.isAdmin.set(this.accountService.isAdmin());
            this.getCourseDetailSections();
        });
        const currentCourse = this.course();
        if (this.irisEnabled() && currentCourse?.isAtLeastInstructor) {
            const irisSettings = await firstValueFrom(this.irisSettingsService.getCourseSettingsWithRateLimit(currentCourse.id!));
            this.irisChatEnabled.set(irisSettings?.settings?.enabled ?? false);
        }
        this.paramSub = this.route.params.subscribe((params) => {
            const courseId = params['courseId'];
            this.fetchCourseStatistics(courseId);
            this.registerChangeInCourses(courseId);
        });
    }

    getGeneralDetailSection(): DetailOverviewSection {
        const currentCourse = this.course();
        const generalDetails: Detail[] = [
            { type: DetailType.Text, title: 'artemisApp.course.title', data: { text: currentCourse?.title } },
            { type: DetailType.Text, title: 'artemisApp.course.shortName', data: { text: currentCourse?.shortName } },
            { type: DetailType.Date, title: 'artemisApp.course.startDate', data: { date: currentCourse?.startDate } },
            { type: DetailType.Date, title: 'artemisApp.course.endDate', data: { date: currentCourse?.endDate } },
            { type: DetailType.Text, title: 'artemisApp.course.semester', data: { text: currentCourse?.semester } },
        ];

        if (currentCourse?.organizations?.length) {
            // insert detail after shortName
            generalDetails.splice(2, 0, {
                type: DetailType.Text,
                title: 'artemisApp.course.organizations',
                data: { text: currentCourse.organizations.map((orga) => orga.name).join(', ') },
            });
        }
        return {
            headline: 'artemisApp.course.detail.sections.general',
            details: generalDetails,
        };
    }

    getComplaintsDetails(): Detail[] {
        const currentCourse = this.course();
        if (currentCourse?.complaintsEnabled) {
            return [
                {
                    type: DetailType.Text,
                    title: 'artemisApp.course.maxComplaints.title',
                    data: { text: currentCourse.maxComplaints },
                },
                {
                    type: DetailType.Text,
                    title: 'artemisApp.course.maxTeamComplaints.title',
                    data: { text: currentCourse.maxTeamComplaints },
                },
                {
                    type: DetailType.Text,
                    title: 'artemisApp.course.maxComplaintTimeDays.title',
                    data: { text: currentCourse.maxComplaintTimeDays },
                },
                {
                    type: DetailType.Text,
                    title: 'artemisApp.course.maxComplaintTextLimit.title',
                    data: { text: currentCourse.maxComplaintTextLimit },
                },
                {
                    type: DetailType.Text,
                    title: 'artemisApp.course.maxComplaintResponseTextLimit.title',
                    data: { text: currentCourse.maxComplaintResponseTextLimit },
                },
            ];
        }
        return [];
    }

    getAthenaDetails(): Detail[] {
        const currentCourse = this.course();
        const athenaDetails: Detail[] = [];
        if (this.isAthenaEnabled()) {
            athenaDetails.push({
                type: DetailType.Boolean,
                title: 'artemisApp.course.restrictedAthenaModulesAccess.label',
                data: { boolean: currentCourse?.restrictedAthenaModulesAccess },
            });
        }
        return athenaDetails;
    }

    getModeDetailSection(): DetailOverviewSection {
        const currentCourse = this.course();
        const complaintsDetails = this.getComplaintsDetails();
        const athenaDetails = this.getAthenaDetails();

        const details: Detail[] = [
            {
                type: DetailType.Text,
                title: 'artemisApp.course.maxPoints.title',
                titleHelpText: 'artemisApp.course.maxPoints.info',
                data: { text: currentCourse?.maxPoints },
            },
            {
                type: DetailType.Text,
                title: 'artemisApp.course.accuracyOfScores.title',
                titleHelpText: 'artemisApp.course.accuracyOfScores.info',
                data: { text: currentCourse?.accuracyOfScores },
            },
            {
                type: DetailType.Text,
                title: 'artemisApp.course.defaultProgrammingLanguage',
                data: { text: currentCourse?.defaultProgrammingLanguage },
            },
            {
                type: DetailType.Boolean,
                title: 'artemisApp.course.testCourse.title',
                data: { boolean: currentCourse?.testCourse },
            },
            ...complaintsDetails,
            ...athenaDetails,
        ];

        // inserting optional details in reversed order, so that no index calculation is needed
        if (currentCourse?.requestMoreFeedbackEnabled) {
            // insert detail after the complaintDetails
            details.splice(4 + complaintsDetails.length, 0, {
                type: DetailType.Text,
                title: 'artemisApp.course.maxRequestMoreFeedbackTimeDays.title',
                data: { text: currentCourse.maxRequestMoreFeedbackTimeDays },
            });
        }

        details.splice(4, 0, {
            type: DetailType.Text,
            title: 'artemisApp.forms.configurationForm.timeZoneInput.label',
            data: { text: currentCourse?.timeZone },
        });

        if (this.ltiEnabled()) {
            // insert lti detail after testCourse detail
            details.splice(4, 0, {
                type: DetailType.Boolean,
                title: 'artemisApp.course.onlineCourse.title',
                data: { boolean: currentCourse?.onlineCourse },
            });
        }

        return {
            headline: 'artemisApp.course.detail.sections.mode',
            details: details,
        };
    }

    getEnrollmentDetailSection(): DetailOverviewSection {
        const currentCourse = this.course();
        const enrollmentDetails: Detail[] = [
            { type: DetailType.Boolean, title: 'artemisApp.course.enrollmentEnabled.title', data: { boolean: currentCourse?.enrollmentEnabled } },
            { type: DetailType.Boolean, title: 'artemisApp.course.unenrollmentEnabled.title', data: { boolean: currentCourse?.unenrollmentEnabled } },
        ];

        if (currentCourse?.enrollmentEnabled) {
            // insert enrollment details after enrollmentEnabled detail
            enrollmentDetails.splice(
                1,
                0,
                { type: DetailType.Date, title: 'artemisApp.course.enrollmentStartDate', data: { date: currentCourse.enrollmentStartDate } },
                { type: DetailType.Date, title: 'artemisApp.course.enrollmentEndDate', data: { date: currentCourse.enrollmentEndDate } },
                {
                    type: DetailType.Markdown,
                    title: 'artemisApp.course.enrollmentConfirmationMessage',
                    data: { innerHtml: this.markdownService.safeHtmlForMarkdown(currentCourse.enrollmentConfirmationMessage) },
                },
            );
        }

        if (currentCourse?.unenrollmentEnabled) {
            // insert unenrollment detail after unenrollmentEnabled detail
            enrollmentDetails.push({ type: DetailType.Date, title: 'artemisApp.course.unenrollmentEndDate', data: { date: currentCourse.unenrollmentEndDate } });
        }
        return {
            headline: 'artemisApp.course.detail.sections.enrollment',
            details: enrollmentDetails,
        };
    }

    getMessagingDetailSection(): DetailOverviewSection {
        const currentCourse = this.course();
        return {
            headline: 'artemisApp.course.detail.sections.messaging',
            details: [
                { type: DetailType.Boolean, title: 'artemisApp.course.courseCommunicationSetting.communicationEnabled.label', data: { boolean: this.communicationEnabled() } },
                { type: DetailType.Boolean, title: 'artemisApp.course.courseCommunicationSetting.messagingEnabled.label', data: { boolean: this.messagingEnabled() } },
                {
                    type: DetailType.Markdown,
                    title: 'artemisApp.course.courseCommunicationSetting.messagingEnabled.codeOfConduct',
                    data: { innerHtml: this.markdownService.safeHtmlForMarkdown(currentCourse?.courseInformationSharingMessagingCodeOfConduct) },
                },
            ],
        };
    }

    getCourseDetailSections() {
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
                this.course.set(courseResponse.body!);
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
        this.eventManager?.destroy(this.eventSubscription);
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
            const currentCourse = this.course();
            if (currentCourse) {
                this.course.set({ ...currentCourse, organizations });
                this.getCourseDetailSections();
            }
        });
    }
}
