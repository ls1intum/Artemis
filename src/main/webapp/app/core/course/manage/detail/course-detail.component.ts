import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { PROFILE_ATHENA, PROFILE_IRIS, PROFILE_LTI } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Subscription, firstValueFrom } from 'rxjs';
import { Course } from 'app/core/shared/entities/course.model';
import { CourseManagementService } from '../course-management.service';
import { CourseManagementDetailViewDto } from 'app/core/course/manage/course-management-detail-view-dto.model';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/shared/service/alert.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { faChartBar, faClipboard, faEye, faFlag, faListAlt, faTable, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { AccountService } from 'app/core/auth/account.service';
import { DetailOverviewSection, DetailType } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { IrisSubSettingsType } from 'app/iris/shared/entities/settings/iris-sub-settings.model';
import { Detail } from 'app/shared/detail-overview-list/detail.model';
import { CourseDetailDoughnutChartComponent } from './course-detail-doughnut-chart.component';
import { CourseDetailLineChartComponent } from './course-detail-line-chart.component';
import { DetailOverviewListComponent } from 'app/shared/detail-overview-list/detail-overview-list.component';

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
    imports: [CourseDetailDoughnutChartComponent, CourseDetailLineChartComponent, DetailOverviewListComponent],
})
export class CourseDetailComponent implements OnInit, OnDestroy {
    private eventManager = inject(EventManager);
    private courseManagementService = inject(CourseManagementService);
    private organizationService = inject(OrganizationManagementService);
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private profileService = inject(ProfileService);
    private accountService = inject(AccountService);
    private irisSettingsService = inject(IrisSettingsService);
    private markdownService = inject(ArtemisMarkdownService);
    private featureToggleService = inject(FeatureToggleService);

    readonly DoughnutChartType = DoughnutChartType;
    readonly FeatureToggle = FeatureToggle;

    courseDTO: CourseManagementDetailViewDto;
    activeStudents?: number[];
    course: Course;

    courseDetailSections: DetailOverviewSection[];

    messagingEnabled: boolean;
    communicationEnabled: boolean;
    irisEnabled = false;
    irisChatEnabled = false;
    ltiEnabled = false;
    isAthenaEnabled = false;
    tutorialEnabled = false;

    isAdmin = false;

    private eventSubscriber: Subscription;
    paramSub: Subscription;

    // Icons
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;
    faTable = faTable;
    faFlag = faFlag;
    faListAlt = faListAlt;
    faChartBar = faChartBar;
    faClipboard = faClipboard;

    /**
     * On init load the course information and subscribe to listen for changes in courses.
     */
    async ngOnInit() {
        this.tutorialEnabled = await firstValueFrom(this.featureToggleService.getFeatureToggleActive(FeatureToggle.TutorialGroups));
        const profileInfo = await firstValueFrom(this.profileService.getProfileInfo());
        this.ltiEnabled = profileInfo?.activeProfiles.includes(PROFILE_LTI);
        this.isAthenaEnabled = profileInfo?.activeProfiles.includes(PROFILE_ATHENA);
        this.irisEnabled = profileInfo?.activeProfiles.includes(PROFILE_IRIS);
        if (this.irisEnabled) {
            const irisSettings = await firstValueFrom(this.irisSettingsService.getGlobalSettings());
            // TODO: Outdated, as we now have a bunch more sub settings
            this.irisChatEnabled = irisSettings?.irisChatSettings?.enabled ?? false;
        }
        this.route.data.subscribe(({ course }) => {
            if (course) {
                this.course = course;
                this.messagingEnabled = !!this.course.courseInformationSharingConfiguration?.includes('MESSAGING');
                this.communicationEnabled = !!this.course.courseInformationSharingConfiguration?.includes('COMMUNICATION');
                this.fetchOrganizations(course.id);
            }
            this.isAdmin = this.accountService.isAdmin();
            this.getCourseDetailSections();
        });
        this.paramSub = this.route.params.subscribe((params) => {
            const courseId = params['courseId'];
            this.fetchCourseStatistics(courseId);
            this.registerChangeInCourses(courseId);
        });
    }

    getGeneralDetailSection(): DetailOverviewSection {
        const generalDetails: Detail[] = [
            { type: DetailType.Text, title: 'artemisApp.course.title', data: { text: this.course.title } },
            { type: DetailType.Text, title: 'artemisApp.course.shortName', data: { text: this.course.shortName } },
            {
                type: DetailType.Link,
                title: 'artemisApp.course.studentGroupName',
                data: {
                    text: `${this.course.studentGroupName} (${this.course.numberOfStudents ?? 0})`,
                    routerLink: this.course.isAtLeastInstructor ? ['/course-management', this.course.id, 'groups', 'students'] : undefined,
                },
            },
            {
                type: DetailType.Link,
                title: 'artemisApp.course.teachingAssistantGroupName',
                data: {
                    text: `${this.course.teachingAssistantGroupName} (${this.course.numberOfTeachingAssistants ?? 0})`,
                    routerLink: this.course.isAtLeastInstructor ? ['/course-management', this.course.id, 'groups', 'tutors'] : undefined,
                },
            },
            {
                type: DetailType.Link,
                title: 'artemisApp.course.editorGroupName',
                data: {
                    text: `${this.course.editorGroupName} (${this.course.numberOfEditors ?? 0})`,
                    routerLink: this.course.isAtLeastInstructor ? ['/course-management', this.course.id, 'groups', 'editors'] : undefined,
                },
            },
            {
                type: DetailType.Link,
                title: 'artemisApp.course.instructorGroupName',
                data: {
                    text: `${this.course.instructorGroupName} (${this.course.numberOfInstructors ?? 0})`,
                    routerLink: this.course.isAtLeastInstructor ? ['/course-management', this.course.id, 'groups', 'instructors'] : undefined,
                },
            },
            { type: DetailType.Date, title: 'artemisApp.course.startDate', data: { date: this.course.startDate } },
            { type: DetailType.Date, title: 'artemisApp.course.endDate', data: { date: this.course.endDate } },
            { type: DetailType.Text, title: 'artemisApp.course.semester', data: { text: this.course.semester } },
        ];

        if (this.course.organizations?.length) {
            // insert detail after shortName
            generalDetails.splice(2, 0, {
                type: DetailType.Text,
                title: 'artemisApp.course.organizations',
                data: { text: this.course.organizations.map((orga) => orga.name).join(', ') },
            });
        }
        return {
            headline: 'artemisApp.course.detail.sections.general',
            details: generalDetails,
        };
    }

    getComplaintsDetails(): Detail[] {
        if (this.course.complaintsEnabled) {
            return [
                {
                    type: DetailType.Text,
                    title: 'artemisApp.course.maxComplaints.title',
                    data: { text: this.course.maxComplaints },
                },
                {
                    type: DetailType.Text,
                    title: 'artemisApp.course.maxTeamComplaints.title',
                    data: { text: this.course.maxTeamComplaints },
                },
                {
                    type: DetailType.Text,
                    title: 'artemisApp.course.maxComplaintTimeDays.title',
                    data: { text: this.course.maxComplaintTimeDays },
                },
                {
                    type: DetailType.Text,
                    title: 'artemisApp.course.maxComplaintTextLimit.title',
                    data: { text: this.course.maxComplaintTextLimit },
                },
                {
                    type: DetailType.Text,
                    title: 'artemisApp.course.maxComplaintResponseTextLimit.title',
                    data: { text: this.course.maxComplaintResponseTextLimit },
                },
            ];
        }
        return [];
    }

    getAthenaDetails(): Detail[] {
        const athenaDetails: Detail[] = [];
        if (this.isAthenaEnabled) {
            athenaDetails.push({
                type: DetailType.Boolean,
                title: 'artemisApp.course.restrictedAthenaModulesAccess.label',
                data: { boolean: this.course.restrictedAthenaModulesAccess },
            });
        }
        return athenaDetails;
    }

    getIrisDetails(): Detail[] {
        const irisDetails: Detail[] = [];
        if (this.irisEnabled && this.irisChatEnabled) {
            irisDetails.push({
                type: DetailType.ProgrammingIrisEnabled,
                title: 'artemisApp.iris.settings.subSettings.enabled.chat',
                data: { course: this.course, disabled: !this.isAdmin, subSettingsType: IrisSubSettingsType.CHAT },
            });
        }
        return irisDetails;
    }

    getModeDetailSection(): DetailOverviewSection {
        const complaintsDetails = this.getComplaintsDetails();
        const athenaDetails = this.getAthenaDetails();
        const irisDetails = this.getIrisDetails();

        const details: Detail[] = [
            { type: DetailType.Text, title: 'artemisApp.course.maxPoints.title', data: { text: this.course.maxPoints } },
            {
                type: DetailType.Text,
                title: 'artemisApp.course.accuracyOfScores',
                data: { text: this.course.accuracyOfScores },
            },
            {
                type: DetailType.Text,
                title: 'artemisApp.course.defaultProgrammingLanguage',
                data: { text: this.course.defaultProgrammingLanguage },
            },
            {
                type: DetailType.Boolean,
                title: 'artemisApp.course.testCourse.title',
                data: { boolean: this.course.testCourse },
            },
            ...complaintsDetails,
            ...athenaDetails,
            ...irisDetails,
        ];

        // inserting optional details in reversed order, so that no index calculation is needed
        if (this.course.requestMoreFeedbackEnabled) {
            // insert detail after the complaintDetails
            details.splice(4 + complaintsDetails.length, 0, {
                type: DetailType.Text,
                title: 'artemisApp.course.maxRequestMoreFeedbackTimeDays.title',
                data: { text: this.course.maxRequestMoreFeedbackTimeDays },
            });
        }

        if (this.tutorialEnabled) {
            // insert tutorial detail after lti detail
            details.splice(4, 0, {
                type: DetailType.Text,
                title: 'artemisApp.forms.configurationForm.timeZoneInput.label',
                titleHelpText: 'artemisApp.forms.configurationForm.timeZoneInput.beta',
                data: { text: this.course.timeZone },
            });
        }

        if (this.ltiEnabled) {
            // insert lti detail after testCourse detail
            details.splice(4, 0, {
                type: DetailType.Boolean,
                title: 'artemisApp.course.onlineCourse.title',
                data: { boolean: this.course.onlineCourse },
            });
        }

        return {
            headline: 'artemisApp.course.detail.sections.mode',
            details: details,
        };
    }

    getEnrollmentDetailSection(): DetailOverviewSection {
        const enrollmentDetails: Detail[] = [
            { type: DetailType.Boolean, title: 'artemisApp.course.enrollmentEnabled.title', data: { boolean: this.course.enrollmentEnabled } },
            { type: DetailType.Boolean, title: 'artemisApp.course.unenrollmentEnabled.title', data: { boolean: this.course.unenrollmentEnabled } },
        ];

        if (this.course.enrollmentEnabled) {
            // insert enrollment details after enrollmentEnabled detail
            enrollmentDetails.splice(
                1,
                0,
                { type: DetailType.Date, title: 'artemisApp.course.enrollmentStartDate', data: { date: this.course.enrollmentStartDate } },
                { type: DetailType.Date, title: 'artemisApp.course.enrollmentEndDate', data: { date: this.course.enrollmentEndDate } },
                {
                    type: DetailType.Markdown,
                    title: 'artemisApp.course.enrollmentConfirmationMessage',
                    data: { innerHtml: this.markdownService.safeHtmlForMarkdown(this.course.enrollmentConfirmationMessage) },
                },
            );
        }

        if (this.course.unenrollmentEnabled) {
            // insert unenrollment detail after unenrollmentEnabled detail
            enrollmentDetails.push({ type: DetailType.Date, title: 'artemisApp.course.unenrollmentEndDate', data: { date: this.course.unenrollmentEndDate } });
        }
        return {
            headline: 'artemisApp.course.detail.sections.enrollment',
            details: enrollmentDetails,
        };
    }

    getMessagingDetailSection(): DetailOverviewSection {
        return {
            headline: 'artemisApp.course.detail.sections.messaging',
            details: [
                { type: DetailType.Boolean, title: 'artemisApp.course.courseCommunicationSetting.communicationEnabled.label', data: { boolean: this.communicationEnabled } },
                { type: DetailType.Boolean, title: 'artemisApp.course.courseCommunicationSetting.messagingEnabled.label', data: { boolean: this.messagingEnabled } },
                {
                    type: DetailType.Markdown,
                    title: 'artemisApp.course.courseCommunicationSetting.messagingEnabled.codeOfConduct',
                    data: { innerHtml: this.markdownService.safeHtmlForMarkdown(this.course.courseInformationSharingMessagingCodeOfConduct) },
                },
            ],
        };
    }

    getCourseDetailSections() {
        const generalSection = this.getGeneralDetailSection();
        const modeSection = this.getModeDetailSection();
        const enrollmentSection = this.getEnrollmentDetailSection();
        const messagingSection = this.getMessagingDetailSection();
        this.courseDetailSections = [generalSection, modeSection, enrollmentSection, messagingSection];
    }

    /**
     * Subscribe to changes in courses and reload the course after a change.
     */
    registerChangeInCourses(courseId: number) {
        this.eventSubscriber = this.eventManager.subscribe('courseListModification', () => {
            this.courseManagementService.find(courseId).subscribe((courseResponse) => {
                this.course = courseResponse.body!;
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
        this.eventManager?.destroy(this.eventSubscriber);
    }

    /**
     * fetch the course specific statistics separately because it takes quite long for larger courses
     */
    private fetchCourseStatistics(courseId: number) {
        this.courseManagementService.getCourseStatisticsForDetailView(courseId).subscribe({
            next: (courseResponse: HttpResponse<CourseManagementDetailViewDto>) => {
                this.courseDTO = courseResponse.body!;
                this.activeStudents = courseResponse.body!.activeStudents;
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    private fetchOrganizations(courseId: number) {
        this.organizationService.getOrganizationsByCourse(courseId).subscribe((organizations) => {
            this.course.organizations = organizations;
            this.getCourseDetailSections();
        });
    }
}
