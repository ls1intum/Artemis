import { AfterViewInit, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Observable, Subscription } from 'rxjs';
import { map } from 'rxjs/operators';
import { NgClass, NgStyle, NgTemplateOutlet } from '@angular/common';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import {
    faChartBar,
    faChevronLeft,
    faChevronRight,
    faCircleNotch,
    faClipboard,
    faEye,
    faFlag,
    faListAlt,
    faQuestion,
    faSync,
    faTable,
    faTimes,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';

import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { PROFILE_ATLAS, PROFILE_IRIS, PROFILE_LOCALCI, PROFILE_LTI } from 'app/app.constants';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseExamArchiveButtonComponent } from 'app/shared/components/course-exam-archive-button/course-exam-archive-button.component';
import { ButtonSize } from 'app/shared/components/button.component';
import { CourseSidebarComponent, SidebarItem } from 'app/core/course/shared/course-sidebar/course-sidebar.component';
import { EventManager } from 'app/shared/service/event-manager.service';
import { Course, isCommunicationEnabled } from 'app/core/shared/entities/course.model';
import { facSidebar } from 'app/shared/icons/icons';
import { CourseConversationsComponent } from 'app/communication/shared/course-conversations.component';
import { BaseCourseContainerComponent } from 'app/core/course/shared/course-base-container/course-base-container.component';
import { CourseSidebarItemService } from 'app/core/course/shared/sidebar-item.service';
import { MetisConversationService } from 'app/communication/metis-conversation.service';

@Component({
    selector: 'jhi-course-management-container',
    templateUrl: './course-management-container.component.html',
    styleUrls: ['course-management-container.component.scss'],
    providers: [MetisConversationService],
    imports: [
        NgClass,
        MatSidenavContainer,
        MatSidenavContent,
        MatSidenav,
        NgbTooltip,
        NgStyle,
        RouterLink,
        RouterOutlet,
        NgTemplateOutlet,
        FaIconComponent,
        TranslateDirective,
        CourseSidebarComponent,
        CourseExamArchiveButtonComponent,
    ],
    standalone: true,
})
export class CourseManagementContainerComponent extends BaseCourseContainerComponent implements OnInit, OnDestroy, AfterViewInit {
    private eventManager = inject(EventManager);
    private featureToggleService = inject(FeatureToggleService);
    private sidebarItemService = inject(CourseSidebarItemService);

    private eventSubscriber: Subscription;
    private featureToggleSub: Subscription;
    private courseSub?: Subscription;

    // Signals for reactive state management
    hasUnreadMessages = signal<boolean>(false);
    communicationRouteLoaded = signal<boolean>(false);
    pageTitle = signal<string>('');
    isNavbarCollapsed = signal<boolean>(false);
    isSidebarCollapsed = signal<boolean>(false);
    isExamStarted = signal<boolean>(false);
    isShownViaLti = signal<boolean>(false);
    hasSidebar = signal<boolean>(false);
    localCIActive = signal<boolean>(false);
    irisEnabled = signal<boolean>(false);
    ltiEnabled = signal<boolean>(false);

    activatedComponentReference = signal<any>(null);

    // Icons
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;
    faTable = faTable;
    faFlag = faFlag;
    faListAlt = faListAlt;
    faChartBar = faChartBar;
    faClipboard = faClipboard;
    faSync = faSync;
    faCircleNotch = faCircleNotch;
    faChevronRight = faChevronRight;
    faChevronLeft = faChevronLeft;
    facSidebar = facSidebar;
    faQuestion = faQuestion;

    FeatureToggle = FeatureToggle;
    CachingStrategy = CachingStrategy;
    ButtonSize = ButtonSize;
    isCommunicationEnabled = isCommunicationEnabled;

    // constructor() {
    //     super();
    //
    //     effect(() => {
    //         if (this.controlConfiguration && this.controls && this.controlsViewContainer) {
    //             this.tryRenderControls();
    //         }
    //     });
    //
    //     effect(() => {
    //         if (this.conversationServiceInstantiated && this.communicationRouteLoaded()) {
    //             this.setUpConversationService();
    //         }
    //     });
    // }

    async ngOnInit() {
        await super.ngOnInit();

        // Subscribe to course modifications and reload the course after a change.
        this.eventSubscriber = this.eventManager.subscribe('courseModification', () => {
            this.subscribeToCourseUpdates(this.courseId()!);
        });

        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.isProduction.set(profileInfo?.inProduction);
                this.isTestServer.set(profileInfo.testServer ?? false);
                this.atlasEnabled.set(profileInfo.activeProfiles.includes(PROFILE_ATLAS));
                this.irisEnabled.set(profileInfo.activeProfiles.includes(PROFILE_IRIS));
                this.ltiEnabled.set(profileInfo.activeProfiles.includes(PROFILE_LTI));
                this.localCIActive.set(profileInfo?.activeProfiles.includes(PROFILE_LOCALCI));
            }
        });
    }

    protected handleCourseIdChange(courseId: number): void {
        this.courseId.set(courseId);
        this.subscribeToCourseUpdates(courseId);
    }

    private subscribeToCourseUpdates(courseId: number) {
        this.courseSub = this.courseManagementService.find(courseId).subscribe((courseResponse) => {
            this.course.set(courseResponse.body!);
        });
    }

    loadCourse(): Observable<void> {
        return this.courseManagementService.findOneForDashboard(this.courseId()!).pipe(
            map((res: HttpResponse<Course>) => {
                if (res.body) {
                    this.course.set(res.body);
                }

                this.setUpConversationService();
            }),
        );
    }

    protected getHasSidebar(): boolean {
        return this.communicationRouteLoaded();
    }

    protected handleComponentActivation(componentRef: any): void {
        // Set activated component reference for specific component types
        this.activatedComponentReference.set(componentRef);
    }

    protected handleToggleSidebar(): void {
        if (!this.activatedComponentReference() || !(this.activatedComponentReference() instanceof CourseConversationsComponent)) {
            return;
        }
        const childRouteComponent = this.activatedComponentReference() as CourseConversationsComponent;
        childRouteComponent.toggleSidebar();
        this.isSidebarCollapsed.set(childRouteComponent.isCollapsed);
    }

    override getSidebarItems(): SidebarItem[] {
        const sidebarItems: SidebarItem[] = [];
        const currentCourse = this.course();

        sidebarItems.push(...this.sidebarItemService.getManagementDefaultItems());
        if (currentCourse?.isAtLeastEditor) {
            sidebarItems.splice(3, 0, this.sidebarItemService.getLecturesItem());
        }

        if (currentCourse?.isAtLeastInstructor && this.irisEnabled()) {
            sidebarItems.push(this.sidebarItemService.getIrisSettingsItem());
        }

        // Communication - only when communication is enabled
        if (currentCourse && isCommunicationEnabled(currentCourse)) {
            sidebarItems.push(this.sidebarItemService.getCommunicationsItem());
        }

        // Tutorial Groups - when configuration exists or user is instructor
        if (currentCourse?.tutorialGroupsConfiguration || currentCourse?.isAtLeastInstructor) {
            sidebarItems.push(this.sidebarItemService.getTutorialGroupsItem());
        }

        // Competency Management - only for instructors with Atlas enabled
        if (currentCourse?.isAtLeastInstructor && this.atlasEnabled()) {
            sidebarItems.push(this.sidebarItemService.getCompetenciesItem());
        }

        // Learning Path - only for instructors with Atlas enabled and learning paths enabled
        if (currentCourse?.isAtLeastInstructor && this.atlasEnabled()) {
            this.featureToggleSub = this.featureToggleService.getFeatureToggleActive(FeatureToggle.LearningPaths).subscribe((isActive) => {
                if (isActive) {
                    sidebarItems.push(this.sidebarItemService.getLearningPathItem());
                }
            });
        }

        sidebarItems.push(this.sidebarItemService.getAssessmentDashboardItem());

        if (currentCourse?.isAtLeastInstructor) {
            sidebarItems.push(this.sidebarItemService.getScoresItem());
        }

        if (currentCourse?.isAtLeastTutor && currentCourse?.faqEnabled) {
            sidebarItems.push(this.sidebarItemService.getFaqItem());
        }

        if (currentCourse?.isAtLeastInstructor && this.localCIActive()) {
            sidebarItems.push(this.sidebarItemService.getBuildQueueItem());
        }

        // LTI Configuration - only for instructors with LTI enabled and online course
        if (this.ltiEnabled() && currentCourse?.onlineCourse && currentCourse?.isAtLeastInstructor) {
            sidebarItems.push(this.sidebarItemService.getLtiConfigurationItem());
        }

        return sidebarItems;
    }

    ngOnDestroy() {
        super.ngOnDestroy();
        this.eventManager.destroy(this.eventSubscriber);
        this.featureToggleSub?.unsubscribe();
        this.courseSub?.unsubscribe();
    }
}
