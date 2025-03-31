import { AfterViewInit, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subscription, of, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { NgClass, NgStyle, NgTemplateOutlet } from '@angular/common';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import { faChartBar, faChevronLeft, faChevronRight, faCircleNotch, faDoorOpen, faEye, faListAlt, faSync, faTable, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';

import { Course, isCommunicationEnabled } from 'app/core/shared/entities/course.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { TeamAssignmentPayload } from 'app/exercise/shared/entities/team/team.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { CourseNotificationOverviewComponent } from 'app/communication/course-notification/course-notification-overview/course-notification-overview.component';
import { CourseUnenrollmentModalComponent } from './course-unenrollment-modal.component';
import { CourseActionItem, CourseSidebarComponent, SidebarItem } from 'app/core/course/shared/course-sidebar/course-sidebar.component';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { TeamService } from 'app/exercise/team/team.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { ExamParticipationService } from 'app/exam/overview/exam-participation.service';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { CourseTitleBarComponent } from 'app/core/course/shared/course-title-bar/course-title-bar.component';
import { BaseCourseContainerComponent } from 'app/core/course/shared/course-base-container/course-base-container.component';
import { CourseSidebarItemService } from 'app/core/course/shared/sidebar-item.service';
import { MetisConversationService } from 'app/communication/metis-conversation.service';
import { CourseExercisesComponent } from 'app/core/course/overview/course-exercises/course-exercises.component';
import { CourseLecturesComponent } from 'app/lecture/shared/course-lectures.component';
import { CourseExamsComponent } from 'app/exam/shared/course-exams/course-exams.component';
import { CourseTutorialGroupsComponent } from 'app/tutorialgroup/shared/course-tutorial-groups.component';
import { CourseConversationsComponent } from 'app/communication/shared/course-conversations.component';

@Component({
    selector: 'jhi-course-overview',
    templateUrl: './course-overview.component.html',
    styleUrls: ['./course-overview.scss', './course-overview.component.scss'],
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
        CourseNotificationOverviewComponent,
        FeatureToggleHideDirective,
        CourseTitleBarComponent,
        CourseSidebarComponent,
    ],
    providers: [MetisConversationService],
})
export class CourseOverviewComponent extends BaseCourseContainerComponent implements OnInit, OnDestroy, AfterViewInit {
    private courseExerciseService = inject(CourseExerciseService);
    private teamService = inject(TeamService);
    private websocketService = inject(WebsocketService);
    private serverDateService = inject(ArtemisServerDateService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);
    private examParticipationService = inject(ExamParticipationService);
    private sidebarItemService = inject(CourseSidebarItemService);

    private toggleSidebarEventSubscription: Subscription;
    private teamAssignmentUpdateListener: Subscription;
    private quizExercisesChannel: string;
    private examStartedSubscription: Subscription;

    courseActionItems = signal<CourseActionItem[]>([]);
    canUnenroll = signal<boolean>(false);
    showRefreshButton = signal<boolean>(false);
    activatedComponentReference = signal<
        CourseExercisesComponent | CourseLecturesComponent | CourseExamsComponent | CourseTutorialGroupsComponent | CourseConversationsComponent | undefined
    >(undefined);

    // Icons
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;
    faTable = faTable;
    faListAlt = faListAlt;
    faChartBar = faChartBar;
    faSync = faSync;
    faCircleNotch = faCircleNotch;
    faChevronRight = faChevronRight;
    faChevronLeft = faChevronLeft;

    FeatureToggle = FeatureToggle;
    CachingStrategy = CachingStrategy;

    async ngOnInit() {
        this.toggleSidebarEventSubscription = this.courseSidebarService.toggleSidebar$.subscribe(() => {
            this.isSidebarCollapsed.update((value) => this.activatedComponentReference()?.isCollapsed ?? !value);
        });

        this.subscription = this.route?.params.subscribe(async (params: { courseId: string }) => {
            const id = Number(params.courseId);
            this.courseId.set(id);
        });
        await super.ngOnInit();

        this.examStartedSubscription = this.examParticipationService.examIsStarted$.subscribe((isStarted: boolean) => {
            this.isExamStarted.set(isStarted);
        });

        await this.initAfterCourseLoad();
        this.courseActionItems.set(this.getCourseActionItems());
        this.isSidebarCollapsed.set(this.activatedComponentReference()?.isCollapsed ?? false);
    }

    handleCourseIdChange(courseId: number): void {
        this.courseId.set(courseId);
    }

    async initAfterCourseLoad() {
        await this.subscribeToTeamAssignmentUpdates();
        this.subscribeForQuizChanges();
    }

    /**
     * Fetch the course from the server including all exercises, lectures, exams and competencies
     */
    loadCourse(refresh = false): Observable<void> {
        this.refreshingCourse.set(refresh);
        const observable = this.courseManagementService.findOneForDashboard(this.courseId()).pipe(
            map((res: HttpResponse<Course>) => {
                if (res.body) {
                    this.course.set(res.body);
                }

                this.setUpConversationService();

                setTimeout(() => this.refreshingCourse.set(false), 500); // ensure min animation duration
            }),
            // catch 403 errors where registration is possible
            catchError((error: HttpErrorResponse) => {
                if (error.status === 403) {
                    this.redirectToCourseRegistrationPageIfCanRegisterOrElseThrow(error);
                    // Emit a default value, for example `undefined`
                    return of(undefined);
                } else {
                    return throwError(() => error);
                }
            }),
            // handle other errors
            catchError((error: HttpErrorResponse) => {
                const errorMessage = error.headers.get('X-artemisApp-message')!;
                this.alertService.addAlert({
                    type: AlertType.DANGER,
                    message: errorMessage,
                    disableTranslation: true,
                });
                return throwError(() => error);
            }),
        );
        // Start fetching, even if we don't subscribe to the result.
        // This enables just calling this method to refresh the course, without subscribing to it:
        this.loadCourseSubscription?.unsubscribe();
        if (refresh) {
            this.loadCourseSubscription = observable.subscribe();
        }
        return observable;
    }

    protected getHasSidebar(): boolean {
        return !!this.route.snapshot.firstChild?.data?.hasSidebar;
    }

    protected handleComponentActivation(componentRef: any): void {
        if (
            componentRef instanceof CourseExercisesComponent ||
            componentRef instanceof CourseLecturesComponent ||
            componentRef instanceof CourseTutorialGroupsComponent ||
            componentRef instanceof CourseExamsComponent ||
            componentRef instanceof CourseConversationsComponent
        ) {
            this.activatedComponentReference.set(componentRef);
        }
        this.getShowRefreshButton();
    }

    handleToggleSidebar(): void {
        if (!this.activatedComponentReference()) {
            return;
        }
        const childRouteComponent = this.activatedComponentReference();
        childRouteComponent?.toggleSidebar();
        this.isSidebarCollapsed.set(childRouteComponent!.isCollapsed);
    }

    getShowRefreshButton(): void {
        this.showRefreshButton.set(this.route.snapshot.firstChild?.data?.showRefreshButton ?? false);
    }

    getSidebarItems(): SidebarItem[] {
        const sidebarItems: SidebarItem[] = [];
        const currentCourse = this.course();

        // Use the service to get sidebar items
        const defaultItems = this.sidebarItemService.getStudentDefaultItems(currentCourse?.studentCourseAnalyticsDashboardEnabled);
        sidebarItems.push(...defaultItems);

        if (currentCourse?.lectures) {
            const lecturesItem = this.sidebarItemService.getLecturesItem();
            sidebarItems.splice(-1, 0, lecturesItem);
        }

        if (currentCourse?.exams && this.hasVisibleExams()) {
            const examsItem = this.sidebarItemService.getExamsItem();
            sidebarItems.unshift(examsItem);
        }

        if (isCommunicationEnabled(currentCourse)) {
            const communicationsItem = this.sidebarItemService.getCommunicationsItem();
            sidebarItems.push(communicationsItem);
        }

        if (this.hasTutorialGroups()) {
            const tutorialGroupsItem = this.sidebarItemService.getTutorialGroupsItem();
            sidebarItems.push(tutorialGroupsItem);
        }

        if (this.atlasEnabled() && this.hasCompetencies()) {
            const competenciesItem = this.sidebarItemService.getCompetenciesItem();
            sidebarItems.push(competenciesItem);

            if (currentCourse?.learningPathsEnabled) {
                const learningPathItem = this.sidebarItemService.getLearningPathItem();
                sidebarItems.push(learningPathItem);
            }
        }

        if (currentCourse?.faqEnabled) {
            const faqItem = this.sidebarItemService.getFaqItem();
            sidebarItems.push(faqItem);
        }
        sidebarItems.push(this.sidebarItemService.getNotificationSettingsItem());

        return sidebarItems;
    }

    getCourseActionItems(): CourseActionItem[] {
        const courseActionItems = [];
        const currentCourse = this.course();

        this.canUnenroll.set(this.canStudentUnenroll() && !currentCourse?.isAtLeastTutor);
        if (this.canUnenroll()) {
            const unenrollItem = this.getUnenrollItem();
            courseActionItems.push(unenrollItem);
        }
        return courseActionItems;
    }

    canStudentUnenroll(): boolean {
        const currentCourse = this.course();
        return !!currentCourse?.unenrollmentEnabled && dayjs().isBefore(currentCourse?.unenrollmentEndDate);
    }

    courseActionItemClick(item?: CourseActionItem) {
        if (item?.action) {
            item.action(item);
        }
    }

    getUnenrollItem(): CourseActionItem {
        return {
            title: 'Unenroll',
            icon: faDoorOpen,
            translation: 'artemisApp.courseOverview.exerciseList.details.unenrollmentButton',
            action: () => this.openUnenrollStudentModal(),
        };
    }

    openUnenrollStudentModal() {
        const modalRef = this.modalService.open(CourseUnenrollmentModalComponent, { size: 'xl' });
        modalRef.componentInstance.course = this.course();
    }

    /**
     * Determines whether the user can register for the course by trying to fetch the for-registration version
     */
    canRegisterForCourse(): Observable<boolean> {
        return this.courseManagementService.findOneForRegistration(this.courseId()).pipe(
            map(() => true),
            catchError((error: HttpErrorResponse) => {
                if (error.status === 403) {
                    return of(false);
                } else {
                    return throwError(() => error);
                }
            }),
        );
    }

    redirectToCourseRegistrationPage() {
        this.router.navigate(['courses', this.courseId(), 'register']);
    }

    redirectToCourseRegistrationPageIfCanRegisterOrElseThrow(error: Error): void {
        this.canRegisterForCourse().subscribe((canRegister) => {
            if (canRegister) {
                this.redirectToCourseRegistrationPage();
            } else {
                throw error;
            }
        });
    }

    /**
     * check if there is at least one exam which should be shown
     */
    hasVisibleExams(): boolean {
        const currentCourse = this.course();
        if (currentCourse?.exams) {
            for (const exam of currentCourse.exams) {
                if (exam.visibleDate && dayjs(exam.visibleDate).isBefore(this.serverDateService.now())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if the course has any competencies or prerequisites
     */
    hasCompetencies(): boolean {
        const currentCourse = this.course();
        return !!(currentCourse?.numberOfCompetencies || currentCourse?.numberOfPrerequisites);
    }

    /**
     * Check if the course has a tutorial groups
     */
    hasTutorialGroups(): boolean {
        return !!this.course()?.numberOfTutorialGroups;
    }

    /**
     * Receives team assignment changes and updates related attributes of the affected exercise
     */
    async subscribeToTeamAssignmentUpdates() {
        const teamAssignmentUpdates = await this.teamService.teamAssignmentUpdates;
        this.teamAssignmentUpdateListener = teamAssignmentUpdates.subscribe((teamAssignment: TeamAssignmentPayload) => {
            const currentCourse = this.course();
            const exercise = currentCourse?.exercises?.find((courseExercise) => courseExercise.id === teamAssignment.exerciseId);
            if (exercise) {
                exercise.studentAssignedTeamId = teamAssignment.teamId;
                exercise.studentParticipations = teamAssignment.studentParticipations;
            }
        });
    }

    subscribeForQuizChanges() {
        // subscribe to quizzes which get visible
        if (!this.quizExercisesChannel) {
            this.quizExercisesChannel = '/topic/courses/' + this.courseId() + '/quizExercises';

            // quizExercise channel => react to changes made to quizExercise (e.g. start date)
            this.websocketService.subscribe(this.quizExercisesChannel);
            this.websocketService.receive(this.quizExercisesChannel).subscribe((quizExercise: QuizExercise) => {
                quizExercise = this.courseExerciseService.convertExerciseDatesFromServer(quizExercise);
                // the quiz was set to visible or started, we should add it to the exercise list and display it at the top
                const currentCourse = this.course();
                if (currentCourse && currentCourse.exercises) {
                    currentCourse.exercises = currentCourse.exercises.filter((exercise) => exercise.id !== quizExercise.id);
                    currentCourse.exercises.push(quizExercise);
                    this.course.set(currentCourse);
                }
            });
        }
    }

    ngOnDestroy() {
        super.ngOnDestroy();
        if (this.teamAssignmentUpdateListener) {
            this.teamAssignmentUpdateListener.unsubscribe();
        }
        if (this.quizExercisesChannel) {
            this.websocketService.unsubscribe(this.quizExercisesChannel);
        }
        this.examStartedSubscription?.unsubscribe();
        this.toggleSidebarEventSubscription?.unsubscribe();
    }
}
