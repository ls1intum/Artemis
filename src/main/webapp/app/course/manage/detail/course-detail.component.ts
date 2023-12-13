import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { PROFILE_ATHENA, PROFILE_LTI } from 'app/app.constants';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { Subscription } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from '../course-management.service';
import { CourseManagementDetailViewDto } from 'app/course/manage/course-management-detail-view-dto.model';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { faChartBar, faClipboard, faEye, faFlag, faListAlt, faTable, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { IrisSubSettingsType } from 'app/entities/iris/settings/iris-sub-settings.model';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { AccountService } from 'app/core/auth/account.service';

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

    messagingEnabled: boolean;
    communicationEnabled: boolean;
    irisEnabled = false;
    irisChatEnabled = false;
    irisHestiaEnabled = false;
    irisCodeEditorEnabled = false;
    ltiEnabled = false;
    isAthenaEnabled = false;

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
    ) {}

    /**
     * On init load the course information and subscribe to listen for changes in courses.
     */
    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.ltiEnabled = profileInfo.activeProfiles.includes(PROFILE_LTI);
            this.isAthenaEnabled = profileInfo.activeProfiles.includes(PROFILE_ATHENA);
            this.irisEnabled = profileInfo.activeProfiles.includes('iris');
            if (this.irisEnabled) {
                this.irisSettingsService.getGlobalSettings().subscribe((settings) => {
                    this.irisChatEnabled = settings?.irisChatSettings?.enabled ?? false;
                    this.irisHestiaEnabled = settings?.irisHestiaSettings?.enabled ?? false;
                    this.irisCodeEditorEnabled = settings?.irisCodeEditorSettings?.enabled ?? false;
                });
            }
        });
        this.route.data.subscribe(({ course }) => {
            if (course) {
                this.course = course;
                this.messagingEnabled = !!this.course.courseInformationSharingConfiguration?.includes('MESSAGING');
                this.communicationEnabled = !!this.course.courseInformationSharingConfiguration?.includes('COMMUNICATION');
            }
            this.isAdmin = this.accountService.isAdmin();
        });
        // There is no course 0 -> will fetch no course if route does not provide different courseId
        let courseId = 0;
        this.paramSub = this.route.params.subscribe((params) => {
            courseId = params['courseId'];
        });
        this.fetchCourseStatistics(courseId);
        this.registerChangeInCourses(courseId);
        this.fetchOrganizations(courseId);
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
