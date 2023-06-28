import { AfterViewInit, ChangeDetectorRef, Component, EmbeddedViewRef, OnDestroy, OnInit, QueryList, TemplateRef, ViewChild, ViewChildren, ViewContainerRef } from '@angular/core';
import { Course, isCommunicationEnabled, isMessagingEnabled } from 'app/entities/course.model';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { CourseManagementService } from '../course/manage/course-management.service';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, Subject, Subscription, catchError, map, of, takeUntil, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { TeamAssignmentPayload } from 'app/entities/team.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import dayjs from 'dayjs/esm';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { faCircleNotch, faSync } from '@fortawesome/free-solid-svg-icons';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { BarControlConfiguration, BarControlConfigurationProvider } from 'app/overview/tab-bar/tab-bar';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { CourseStorageService } from 'app/course/manage/course-storage.service';

@Component({
    selector: 'jhi-course-overview',
    templateUrl: './course-overview.component.html',
    styleUrls: ['course-overview.scss', './tab-bar/tab-bar.scss'],
    providers: [MetisConversationService],
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
    faSync = faSync;
    faCircleNotch = faCircleNotch;
    FeatureToggle = FeatureToggle;

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
        private profileService: ProfileService,
        private tutorialGroupService: TutorialGroupsService,
        private tutorialGroupsConfigurationService: TutorialGroupsConfigurationService,
        private metisConversationService: MetisConversationService,
        private router: Router,
    ) {}

    async ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.course = this.courseStorageService.getCourse(this.courseId);

        await this.loadCourse().toPromise();
        await this.initAfterCourseLoad();
    }

    async initAfterCourseLoad() {
        await this.subscribeToTeamAssignmentUpdates();
        this.subscribeForQuizChanges();
    }

    private setUpConversationService() {
        if (!isMessagingEnabled(this.course)) {
            return;
        }

        if (!this.conversationServiceInstantiated && this.messagesRouteLoaded) {
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
        } else if (!this.checkedForUnreadMessages) {
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
        this.messagesRouteLoaded = this.route.snapshot.firstChild?.routeConfig?.path === 'messages';

        this.setUpConversationService();

        if (componentRef.controlConfiguration) {
            const provider = componentRef as BarControlConfigurationProvider;
            this.controlConfiguration = provider.controlConfiguration as BarControlConfiguration;

            // Listen for changes to the control configuration; works for initial config as well
            this.controlsSubscription =
                this.controlConfiguration.subject?.subscribe((controls: TemplateRef<any>) => {
                    this.controls = controls;
                    this.tryRenderControls();
                    // Since we might be pulling data upwards during a render cycle, we need to re-run change detection
                    this.changeDetectorRef.detectChanges();
                }) || undefined;
        }
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
                    return throwError(error);
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
        const observable = this.courseService.findOneForDashboard(this.courseId, refresh).pipe(
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
                    return of();
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
        this.ngUnsubscribe.next();
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
        return !!(this.course?.competencies?.length || this.course?.prerequisites?.length);
    }

    /**
     * Check if the course has a tutorial groups
     */
    hasTutorialGroups(): boolean {
        return !!this.course?.tutorialGroups?.length;
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
