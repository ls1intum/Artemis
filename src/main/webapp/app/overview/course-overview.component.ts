import { AfterViewInit, ChangeDetectorRef, Component, EmbeddedViewRef, OnDestroy, OnInit, QueryList, TemplateRef, ViewChild, ViewChildren, ViewContainerRef } from '@angular/core';
import { Course, isCommunicationEnabled, isMessagingEnabled } from 'app/entities/course.model';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { CourseManagementService } from '../course/manage/course-management.service';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, Subject, Subscription, catchError, firstValueFrom, map, of, takeUntil, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { TeamAssignmentPayload } from 'app/entities/team.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import dayjs from 'dayjs/esm';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import {
    IconDefinition,
    faChalkboardUser,
    faChartBar,
    faChevronRight,
    faCircleNotch,
    faClipboard,
    faComment,
    faComments,
    faEye,
    faFilePdf,
    faFlag,
    faGraduationCap,
    faListAlt,
    faListCheck,
    faNetworkWired,
    faPersonChalkboard,
    faSync,
    faTable,
    faTimes,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { BarControlConfiguration, BarControlConfigurationProvider } from 'app/shared/tab-bar/tab-bar';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { CourseAccessStorageService } from 'app/course/course-access-storage.service';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { animate, style, transition, trigger } from '@angular/animations';

interface SidebarItem {
    routerLink: string;
    icon?: IconDefinition;
    name: string;
    testId?: string;
    translation: string;
    hasInOrionProperty?: boolean;
    showInOrionWindow?: boolean;
    guidedTour?: boolean;
    featureToggle?: FeatureToggle;
}

@Component({
    selector: 'jhi-course-overview',
    templateUrl: './course-overview.component.html',
    styleUrls: ['course-overview.scss', 'course-overview.component.scss'],
    providers: [MetisConversationService],
    animations: [
        trigger('slideIn', [
            transition(':enter', [style({ width: 'translateX(-100%)' }), animate(0, style({ transform: 'translateX(0)' }))]),
            transition(':leave', [animate(0, style({ transform: 'translateX(-100%)' }))]),
        ]),
    ],
})
export class CourseOverviewComponent implements OnInit, OnDestroy, AfterViewInit {
    private ngUnsubscribe = new Subject<void>();

    private courseId: number;
    private subscription: Subscription;
    public course?: Course;
    public refreshingCourse = false;
    private teamAssignmentUpdateListener: Subscription;
    private quizExercisesChannel: string;
    public hasUnreadMessages: boolean;
    public messagesRouteLoaded: boolean;
    public communicationRouteLoaded: boolean;
    public isProduction = true;
    public isTestServer = false;
    public pageTitle: string;
    public sidebarItems: SidebarItem[];
    public isNotManagementView: boolean;
    isCollapsed = false;

    private conversationServiceInstantiated = false;
    private checkedForUnreadMessages = false;

    // Rendered embedded view for controls in the bar so we can destroy it if needed
    private controlsEmbeddedView?: EmbeddedViewRef<any>;
    // Subscription for the course fetching
    private loadCourseSubscription?: Subscription;
    // Subscription to listen to changes on the control configuration
    private controlsSubscription?: Subscription;
    // Subscription to listen for the ng-container for controls to be mounted
    private vcSubscription?: Subscription;
    // The current controls template from the sub-route component to render
    private controls?: TemplateRef<any>;
    // The current controls configuration from the sub-route component
    public controlConfiguration?: BarControlConfiguration;

    // ng-container mount point extracted from our own template so we can render sth in it
    @ViewChild('controlsViewContainer', { read: ViewContainerRef }) controlsViewContainer: ViewContainerRef;
    // Using a list query to be able to listen for changes (late mount); need both as this only returns native nodes
    @ViewChildren('controlsViewContainer') controlsViewContainerAsList: QueryList<ViewContainerRef>;

    // Icons
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;
    faTable = faTable;
    faFlag = faFlag;
    faListAlt = faListAlt;
    faChartBar = faChartBar;
    faFilePdf = faFilePdf;
    faComment = faComment;
    faComments = faComments;
    faClipboard = faClipboard;
    faGraduationCap = faGraduationCap;
    faSync = faSync;
    faCircleNotch = faCircleNotch;
    faNetworkWired = faNetworkWired;
    faChalkboardUser = faChalkboardUser;
    faChevronRight = faChevronRight;
    faListCheck = faListCheck;

    FeatureToggle = FeatureToggle;
    CachingStrategy = CachingStrategy;

    readonly isMessagingEnabled = isMessagingEnabled;
    readonly isCommunicationEnabled = isCommunicationEnabled;

    constructor(
        private courseService: CourseManagementService,
        private courseExerciseService: CourseExerciseService,
        private courseStorageService: CourseStorageService,
        private route: ActivatedRoute,
        private teamService: TeamService,
        private jhiWebsocketService: JhiWebsocketService,
        private serverDateService: ArtemisServerDateService,
        private alertService: AlertService,
        private changeDetectorRef: ChangeDetectorRef,
        private metisConversationService: MetisConversationService,
        private router: Router,
        private courseAccessStorageService: CourseAccessStorageService,
        private profileService: ProfileService,
    ) {}

    async ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });
        this.profileService.getProfileInfo()?.subscribe((profileInfo) => {
            this.isProduction = profileInfo.inProduction;
            this.isTestServer = profileInfo.testServer ?? false;
        });
        this.course = this.courseStorageService.getCourse(this.courseId);
        this.isNotManagementView = !this.router.url.startsWith('/course-management');
        // Notify the course access storage service that the course has been accessed
        this.courseAccessStorageService.onCourseAccessed(this.courseId);

        await firstValueFrom(this.loadCourse());
        await this.initAfterCourseLoad();
        this.sidebarItems = this.getSidebarItems();
    }

    getSidebarItems(): SidebarItem[] {
        const sidebarItems = this.getDefaultItems();
        if (this.course?.lectures) {
            const lecturesItem: SidebarItem = this.getLecturesItems();
            sidebarItems.splice(-1, 0, lecturesItem);
        }
        if (this.course?.exams && this.hasVisibleExams()) {
            const examsItem: SidebarItem = this.getExamsItems();
            sidebarItems.unshift(examsItem);
        }
        if (isCommunicationEnabled(this.course)) {
            const communicationItem: SidebarItem = this.getCommunicationItems();
            sidebarItems.push(communicationItem);
        }

        if (isMessagingEnabled(this.course) || isCommunicationEnabled(this.course)) {
            const messagesItem: SidebarItem = this.getMessagesItems();
            sidebarItems.push(messagesItem);
        }

        if (this.hasTutorialGroups()) {
            const tutorialGroupsItem: SidebarItem = this.getTutorialGroupsItems();
            sidebarItems.push(tutorialGroupsItem);
        }

        if (this.hasCompetencies()) {
            const competenciesItem: SidebarItem = this.getCompetenciesItems();
            sidebarItems.push(competenciesItem);
            if (this.course?.learningPathsEnabled) {
                const learningPathItem: SidebarItem = this.getLearningPathItems();
                sidebarItems.push(learningPathItem);
            }
        }

        return sidebarItems;
    }

    getLecturesItems() {
        const lecturesItem: SidebarItem = {
            routerLink: 'lectures',
            icon: faChalkboardUser,
            name: 'Lectures',
            translation: 'artemisApp.courseOverview.menu.lectures',
            hasInOrionProperty: true,
            showInOrionWindow: false,
        };
        return lecturesItem;
    }
    getExamsItems() {
        const examsItem: SidebarItem = {
            routerLink: 'exams',
            icon: faGraduationCap,
            name: 'Exams',
            testId: 'exam-tab',
            translation: 'artemisApp.courseOverview.menu.exams',
            hasInOrionProperty: true,
            showInOrionWindow: false,
        };
        return examsItem;
    }
    getCommunicationItems() {
        const communicationItem: SidebarItem = {
            routerLink: 'discussion',
            icon: faComment,
            name: 'Communication',
            translation: 'artemisApp.courseOverview.menu.communication',
            hasInOrionProperty: true,
            showInOrionWindow: false,
        };
        return communicationItem;
    }
    getMessagesItems() {
        const messagesItem: SidebarItem = {
            routerLink: 'messages',
            icon: faComments,
            name: 'Messages',
            translation: 'artemisApp.courseOverview.menu.messages',
            hasInOrionProperty: true,
            showInOrionWindow: false,
        };
        return messagesItem;
    }
    getTutorialGroupsItems() {
        const tutorialGroupsItem: SidebarItem = {
            routerLink: 'tutorial-groups',
            icon: faPersonChalkboard,
            name: 'Exercises',
            translation: 'artemisApp.courseOverview.menu.tutorialGroups',
            hasInOrionProperty: true,
            showInOrionWindow: false,
            featureToggle: FeatureToggle.TutorialGroups,
        };
        return tutorialGroupsItem;
    }
    getCompetenciesItems() {
        const competenciesItem: SidebarItem = {
            routerLink: 'competencies',
            icon: faFlag,
            name: 'Competencies',
            translation: 'artemisApp.courseOverview.menu.competencies',
            hasInOrionProperty: true,
            showInOrionWindow: false,
        };
        return competenciesItem;
    }
    getLearningPathItems() {
        const learningPathItem: SidebarItem = {
            routerLink: 'learning-path',
            icon: faNetworkWired,
            name: 'Learning Path',
            translation: 'artemisApp.courseOverview.menu.learningPath',
            hasInOrionProperty: true,
            showInOrionWindow: false,
            featureToggle: FeatureToggle.LearningPaths,
        };
        return learningPathItem;
    }

    getDefaultItems() {
        const exercisesItem: SidebarItem = {
            routerLink: 'exercises',
            icon: faListCheck,
            name: 'Exercises',
            translation: 'artemisApp.courseOverview.menu.exercises',
        };

        const statisticsItem: SidebarItem = {
            routerLink: 'statistics',
            icon: faListAlt,
            name: 'Statistics',
            translation: 'artemisApp.courseOverview.menu.statistics',
            hasInOrionProperty: true,
            showInOrionWindow: false,
            guidedTour: true,
        };

        return [exercisesItem, statisticsItem];
    }

    async initAfterCourseLoad() {
        await this.subscribeToTeamAssignmentUpdates();
        this.subscribeForQuizChanges();
    }

    private setUpConversationService() {
        if (!isMessagingEnabled(this.course) && !isCommunicationEnabled(this.course)) {
            return;
        }

        if (!this.conversationServiceInstantiated && (this.messagesRouteLoaded || this.communicationRouteLoaded)) {
            this.metisConversationService
                .setUpConversationService(this.course!)
                .pipe(takeUntil(this.ngUnsubscribe))
                .subscribe({
                    complete: () => {
                        this.conversationServiceInstantiated = true;
                        // service is fully set up, now we can subscribe to the respective observables
                        this.subscribeToHasUnreadMessages();
                    },
                });
        } else if (!this.checkedForUnreadMessages && isMessagingEnabled(this.course)) {
            this.metisConversationService.checkForUnreadMessages(this.course!);
            this.subscribeToHasUnreadMessages();
            this.checkedForUnreadMessages = true;
        }
    }

    ngAfterViewInit() {
        // Check if controls mount point is available, if not, wait for it
        if (this.controlsViewContainer) {
            this.tryRenderControls();
        } else {
            this.vcSubscription = this.controlsViewContainerAsList.changes.subscribe(() => this.tryRenderControls());
        }
    }

    private subscribeToHasUnreadMessages() {
        this.metisConversationService.hasUnreadMessages$.pipe().subscribe((hasUnreadMessages: boolean) => {
            this.hasUnreadMessages = hasUnreadMessages ?? false;
        });
    }

    /**
     * Accepts a component reference of the subcomponent rendered based on the current route.
     * If it provides a controlsConfiguration, we try to render the controls component
     * @param componentRef the sub route component that has been mounted into the router outlet
     */
    onSubRouteActivate(componentRef: any) {
        this.getPageTitle();
        this.messagesRouteLoaded = this.route.snapshot.firstChild?.routeConfig?.path === 'messages';
        this.communicationRouteLoaded = this.route.snapshot.firstChild?.routeConfig?.path === 'discussion';

        this.setUpConversationService();

        if (componentRef.controlConfiguration) {
            const provider = componentRef as BarControlConfigurationProvider;
            this.controlConfiguration = provider.controlConfiguration as BarControlConfiguration;

            // Listen for changes to the control configuration; works for initial config as well
            this.controlsSubscription =
                this.controlConfiguration.subject?.subscribe((controls: TemplateRef<any>) => {
                    this.controls = controls;
                    this.tryRenderControls();
                }) || undefined;
        }
        // Since we change the pageTitle + might be pulling data upwards during a render cycle, we need to re-run change detection
        this.changeDetectorRef.detectChanges();
    }
    getPageTitle(): void {
        const routePageTitle: string = this.route.snapshot.firstChild?.data?.pageTitle;
        this.pageTitle = routePageTitle?.substring(routePageTitle.indexOf('.') + 1);
    }

    /**
     * Removes the controls component from the DOM and cancels the listener for controls changes.
     * Called by the router outlet as soon as the currently mounted component is removed
     */
    onSubRouteDeactivate() {
        this.removeCurrentControlsView();
        this.controls = undefined;
        this.controlConfiguration = undefined;
        this.controlsSubscription?.unsubscribe();
        this.changeDetectorRef.detectChanges();
    }

    private removeCurrentControlsView() {
        this.controlsEmbeddedView?.detach();
        this.controlsEmbeddedView?.destroy();
    }

    /**
     * Mounts the controls as specified by the currently mounted sub-route component to the ng-container in the top bar
     * if all required data is available.
     */
    tryRenderControls() {
        if (this.controlConfiguration && this.controls && this.controlsViewContainer) {
            this.removeCurrentControlsView();
            this.controlsEmbeddedView = this.controlsViewContainer.createEmbeddedView(this.controls);
            this.controlsEmbeddedView.detectChanges();
        }
    }

    /**
     * Determines whether the user can register for the course by trying to fetch the for-registration version
     */
    canRegisterForCourse(): Observable<boolean> {
        return this.courseService.findOneForRegistration(this.courseId).pipe(
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
        this.router.navigate(['courses', this.courseId, 'register']);
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
     * Fetch the course from the server including all exercises, lectures, exams and competencies
     * @param refresh Whether this is a force refresh (displays loader animation)
     */
    loadCourse(refresh = false): Observable<void> {
        this.refreshingCourse = refresh;
        const observable = this.courseService.findOneForDashboard(this.courseId).pipe(
            map((res: HttpResponse<Course>) => {
                if (res.body) {
                    this.course = res.body;
                }

                if (refresh) {
                    this.setUpConversationService();
                }

                setTimeout(() => (this.refreshingCourse = false), 500); // ensure min animation duration
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

    ngOnDestroy() {
        if (this.teamAssignmentUpdateListener) {
            this.teamAssignmentUpdateListener.unsubscribe();
        }
        if (this.quizExercisesChannel) {
            this.jhiWebsocketService.unsubscribe(this.quizExercisesChannel);
        }
        this.loadCourseSubscription?.unsubscribe();
        this.controlsSubscription?.unsubscribe();
        this.vcSubscription?.unsubscribe();
        this.subscription?.unsubscribe();
        this.ngUnsubscribe.next(undefined);
        this.ngUnsubscribe.complete();
    }

    subscribeForQuizChanges() {
        // subscribe to quizzes which get visible
        if (!this.quizExercisesChannel) {
            this.quizExercisesChannel = '/topic/courses/' + this.courseId + '/quizExercises';

            // quizExercise channel => react to changes made to quizExercise (e.g. start date)
            this.jhiWebsocketService.subscribe(this.quizExercisesChannel);
            this.jhiWebsocketService.receive(this.quizExercisesChannel).subscribe((quizExercise: QuizExercise) => {
                quizExercise = this.courseExerciseService.convertExerciseDatesFromServer(quizExercise);
                // the quiz was set to visible or started, we should add it to the exercise list and display it at the top
                if (this.course && this.course.exercises) {
                    this.course.exercises = this.course.exercises.filter((exercise) => exercise.id !== quizExercise.id);
                    this.course.exercises.push(quizExercise);
                }
            });
        }
    }

    /**
     * check if there is at least one exam which should be shown
     */
    hasVisibleExams(): boolean {
        if (this.course?.exams) {
            for (const exam of this.course.exams) {
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
        return !!(this.course?.numberOfCompetencies || this.course?.numberOfPrerequisites);
    }

    /**
     * Check if the course has a tutorial groups
     */
    hasTutorialGroups(): boolean {
        return !!this.course?.numberOfTutorialGroups;
    }

    /**
     * Receives team assignment changes and updates related attributes of the affected exercise
     */
    async subscribeToTeamAssignmentUpdates() {
        const teamAssignmentUpdates = await this.teamService.teamAssignmentUpdates;
        this.teamAssignmentUpdateListener = teamAssignmentUpdates.subscribe((teamAssignment: TeamAssignmentPayload) => {
            const exercise = this.course?.exercises?.find((courseExercise) => courseExercise.id === teamAssignment.exerciseId);
            if (exercise) {
                exercise.studentAssignedTeamId = teamAssignment.teamId;
                exercise.studentParticipations = teamAssignment.studentParticipations;
            }
        });
    }
}
