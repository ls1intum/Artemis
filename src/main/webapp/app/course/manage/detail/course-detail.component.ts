import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { PROFILE_LTI } from 'app/app.constants';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { Subscription, firstValueFrom } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from '../course-management.service';
import { CourseManagementDetailViewDto } from 'app/course/manage/course-management-detail-view-dto.model';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { faChartBar, faClipboard, faEye, faFlag, faListAlt, faTable, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { IrisSubSettingsType } from 'app/entities/iris/settings/iris-sub-settings.model';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { AccountService } from 'app/core/auth/account.service';
import { DetailOverviewSection, DetailType } from 'app/detail-overview-list/detail-overview-list.component';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';

export enum DoughnutChartType {
    ASSESSMENT = 'ASSESSMENT',
    COMPLAINTS = 'COMPLAINTS',
    FEEDBACK = 'FEEDBACK',
    AVERAGE_COURSE_SCORE = 'AVERAGE_COURSE_SCORE',
    AVERAGE_EXERCISE_SCORE = 'AVERAGE_EXERCISE_SCORE',
    PARTICIPATIONS = 'PARTICIPATIONS',
    QUESTIONS = 'QUESTIONS',
}

@Component({
    selector: 'jhi-course-detail',
    templateUrl: './course-detail.component.html',
    styleUrls: ['./course-detail.component.scss'],
})
export class CourseDetailComponent implements OnInit, OnDestroy {
    readonly DoughnutChartType = DoughnutChartType;
    readonly FeatureToggle = FeatureToggle;
    readonly CHAT = IrisSubSettingsType.CHAT;
    readonly HESTIA = IrisSubSettingsType.HESTIA;
    readonly CODE_EDITOR = IrisSubSettingsType.CODE_EDITOR;

    courseDTO: CourseManagementDetailViewDto;
    activeStudents?: number[];
    course: Course;

    courseDetailSections: DetailOverviewSection[];

    messagingEnabled: boolean;
    communicationEnabled: boolean;
    irisEnabled = false;
    irisChatEnabled = false;
    irisHestiaEnabled = false;
    irisCodeEditorEnabled = false;
    ltiEnabled = false;
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

    constructor(
        private eventManager: EventManager,
        private courseManagementService: CourseManagementService,
        private organizationService: OrganizationManagementService,
        private route: ActivatedRoute,
        private alertService: AlertService,
        private profileService: ProfileService,
        private accountService: AccountService,
        private irisSettingsService: IrisSettingsService,
        private translateService: TranslateService,
        private markdownService: ArtemisMarkdownService,
        private featureToggleService: FeatureToggleService,
    ) {}

    /**
     * On init load the course information and subscribe to listen for changes in courses.
     */
    async ngOnInit() {
        this.tutorialEnabled = await firstValueFrom(this.featureToggleService.getFeatureToggleActive(FeatureToggle.TutorialGroups));
        const profileInfo = await firstValueFrom(this.profileService.getProfileInfo());
        this.ltiEnabled = profileInfo?.activeProfiles.includes(PROFILE_LTI);
        this.irisEnabled = profileInfo?.activeProfiles.includes('iris');
        if (this.irisEnabled) {
            const irisSettings = await firstValueFrom(this.irisSettingsService.getGlobalSettings());
            this.irisChatEnabled = irisSettings?.irisChatSettings?.enabled ?? false;
            this.irisHestiaEnabled = irisSettings?.irisHestiaSettings?.enabled ?? false;
            this.irisCodeEditorEnabled = irisSettings?.irisCodeEditorSettings?.enabled ?? false;
        }
        this.route.data.subscribe(({ course }) => {
            if (course) {
                this.course = course;
                this.messagingEnabled = !!this.course.courseInformationSharingConfiguration?.includes('MESSAGING');
                this.communicationEnabled = !!this.course.courseInformationSharingConfiguration?.includes('COMMUNICATION');
                this.fetchOrganizations(course.id);
                this.getCourseDetailSections();
            }
            this.isAdmin = this.accountService.isAdmin();
        });
        this.paramSub = this.route.params.subscribe((params) => {
            const courseId = params['courseId'];
            this.fetchCourseStatistics(courseId);
            this.registerChangeInCourses(courseId);
        });
    }

    getCourseDetailSections() {
        const course = this.course;
        this.courseDetailSections = [
            {
                headline: 'artemisApp.course.detail.sections.general',
                details: [
                    { type: DetailType.Text, title: 'artemisApp.course.title', data: { text: course.title } },
                    { type: DetailType.Text, title: 'artemisApp.course.shortName', data: { text: course.shortName } },
                    !!course.organizations?.length && {
                        type: DetailType.Text,
                        title: 'artemisApp.course.organizations',
                        data: { text: course.organizations.map((orga) => orga.name).join(', ') },
                    },
                    {
                        type: DetailType.Link,
                        title: 'artemisApp.course.studentGroupName',
                        data: {
                            text: `${course.studentGroupName} (${course.numberOfStudents ?? 0})`,
                            routerLink: course.isAtLeastInstructor && ['/course-management', course.id, 'groups', 'students'],
                        },
                    },
                    {
                        type: DetailType.Link,
                        title: 'artemisApp.course.teachingAssistantGroupName',
                        data: {
                            text: `${course.teachingAssistantGroupName} (${course.numberOfTeachingAssistants ?? 0})`,
                            routerLink: course.isAtLeastInstructor && ['/course-management', course.id, 'groups', 'tutors'],
                        },
                    },
                    {
                        type: DetailType.Link,
                        title: 'artemisApp.course.editorGroupName',
                        data: {
                            text: `${course.editorGroupName} (${course.numberOfEditors ?? 0})`,
                            routerLink: course.isAtLeastInstructor && ['/course-management', course.id, 'groups', 'editors'],
                        },
                    },
                    {
                        type: DetailType.Link,
                        title: 'artemisApp.course.instructorGroupName',
                        data: {
                            text: `${course.instructorGroupName} (${course.numberOfInstructors ?? 0})`,
                            routerLink: course.isAtLeastInstructor && ['/course-management', course.id, 'groups', 'instructors'],
                        },
                    },
                    { type: DetailType.Date, title: 'artemisApp.course.startDate', data: { date: course.startDate } },
                    { type: DetailType.Date, title: 'artemisApp.course.endDate', data: { date: course.endDate } },
                    { type: DetailType.Text, title: 'artemisApp.course.semester', data: { text: course.semester } },
                ].filter(Boolean),
            } as DetailOverviewSection,
            {
                headline: 'artemisApp.course.detail.sections.mode',
                details: [
                    { type: DetailType.Text, title: 'artemisApp.course.maxPoints.title', data: { text: course.maxPoints } },
                    { type: DetailType.Text, title: 'artemisApp.course.accuracyOfScores', data: { text: course.accuracyOfScores } },
                    { type: DetailType.Text, title: 'artemisApp.course.defaultProgrammingLanguage', data: { text: course.defaultProgrammingLanguage?.toLowerCase() } },
                    { type: DetailType.Boolean, title: 'artemisApp.course.testCourse.title', data: { boolean: course.testCourse } },
                    this.ltiEnabled &&
                        !course.onlineCourse &&
                        !course.isAtLeastInstructor && { type: DetailType.Boolean, title: 'artemisApp.course.onlineCourse.title', data: { boolean: course.onlineCourse } },
                    this.ltiEnabled &&
                        course.onlineCourse &&
                        course.isAtLeastInstructor && {
                            type: DetailType.Link,
                            title: 'artemisApp.course.onlineCourse.title',
                            data: {
                                text: this.translateService.instant('artemisApp.course.ltiConfiguration'),
                                routerLink: ['/course-management', course?.id, 'lti-configuration'],
                            },
                        },
                    this.tutorialEnabled && {
                        type: DetailType.Text,
                        title: 'artemisApp.forms.configurationForm.timeZoneInput.label',
                        titleHelpText: 'artemisApp.forms.configurationForm.timeZoneInput.beta',
                        data: { text: course.timeZone },
                    },
                    course.complaintsEnabled && {
                        type: DetailType.Text,
                        title: 'artemisApp.course.maxComplaints.title',
                        data: { text: course.maxComplaints },
                    },
                    course.complaintsEnabled && {
                        type: DetailType.Text,
                        title: 'artemisApp.course.maxTeamComplaints.title',
                        data: { text: course.maxTeamComplaints },
                    },
                    course.complaintsEnabled && {
                        type: DetailType.Text,
                        title: 'artemisApp.course.maxComplaintTimeDays.title',
                        data: { text: course.maxComplaintTimeDays },
                    },
                    course.complaintsEnabled && {
                        type: DetailType.Text,
                        title: 'artemisApp.course.maxComplaintTextLimit.title',
                        data: { text: course.maxComplaintTextLimit },
                    },
                    course.complaintsEnabled && {
                        type: DetailType.Text,
                        title: 'artemisApp.course.maxComplaintResponseTextLimit.title',
                        data: { text: course.maxComplaintResponseTextLimit },
                    },
                    course.requestMoreFeedbackEnabled && {
                        type: DetailType.Text,
                        title: 'artemisApp.course.maxRequestMoreFeedbackTimeDays.title',
                        data: { text: course.maxRequestMoreFeedbackTimeDays },
                    },
                    // TODO: Enable in future PR
                    false &&
                        this.irisEnabled &&
                        this.irisHestiaEnabled && {
                            type: DetailType.ProgrammingIrisEnabled,
                            title: 'artemisApp.iris.settings.subSettings.enabled.hesita',
                            data: { course, disabled: !this.isAdmin, subSettingsType: this.HESTIA },
                        },
                    // TODO: Enable in future PR
                    false &&
                        this.irisEnabled &&
                        this.irisCodeEditorEnabled && {
                            type: DetailType.ProgrammingIrisEnabled,
                            title: 'artemisApp.iris.settings.subSettings.enabled.codeEditor',
                            data: { course, disabled: !this.isAdmin, subSettingsType: this.CODE_EDITOR },
                        },
                ].filter(Boolean),
            } as DetailOverviewSection,
            {
                headline: 'artemisApp.course.detail.sections.enrollment',
                details: [
                    { type: DetailType.Boolean, title: 'artemisApp.course.registrationEnabled.title', data: { boolean: course.enrollmentEnabled } },
                    course.enrollmentEnabled && { type: DetailType.Date, title: 'artemisApp.course.enrollmentStartDate', data: { date: course.enrollmentStartDate } },
                    course.enrollmentEnabled && { type: DetailType.Date, title: 'artemisApp.course.enrollmentEndDate', data: { date: course.enrollmentEndDate } },
                    course.enrollmentEnabled && {
                        type: DetailType.Markdown,
                        title: 'artemisApp.course.registrationConfirmationMessage',
                        data: { innerHtm: this.markdownService.safeHtmlForMarkdown(course.enrollmentConfirmationMessage) },
                    },
                    { type: DetailType.Boolean, title: 'artemisApp.course.unenrollmentEnabled.title', data: { boolean: course.unenrollmentEnabled } },
                    course.unenrollmentEnabled && { type: DetailType.Date, title: 'artemisApp.course.unenrollmentEndDate', data: { date: course.unenrollmentEndDate } },
                ].filter(Boolean),
            } as DetailOverviewSection,
            {
                headline: 'artemisApp.course.detail.sections.messaging',
                details: [
                    { type: DetailType.Boolean, title: 'artemisApp.course.courseCommunicationSetting.communicationEnabled.label', data: { boolean: this.communicationEnabled } },
                    { type: DetailType.Boolean, title: 'artemisApp.course.courseCommunicationSetting.messagingEnabled.label', data: { boolean: this.messagingEnabled } },
                    {
                        type: DetailType.Markdown,
                        title: 'artemisApp.course.courseCommunicationSetting.messagingEnabled.codeOfConduct',
                        data: { innerHtml: this.markdownService.safeHtmlForMarkdown(course.courseInformationSharingMessagingCodeOfConduct) },
                    },
                ],
            },
        ];
    }

    /**
     * Subscribe to changes in courses and reload the course after a change.
     */
    registerChangeInCourses(courseId: number) {
        this.eventSubscriber = this.eventManager.subscribe('courseListModification', () => {
            this.courseManagementService.find(courseId).subscribe((courseResponse) => {
                this.course = courseResponse.body!;
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
        this.eventManager.destroy(this.eventSubscriber);
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
        });
    }
}
