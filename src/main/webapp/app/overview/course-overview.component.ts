import { AfterViewInit, ChangeDetectorRef, Component, EmbeddedViewRef, OnDestroy, OnInit, QueryList, TemplateRef, ViewChild, ViewChildren, ViewContainerRef } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from '../course/manage/course-management.service';
import { ActivatedRoute } from '@angular/router';
import { forkJoin, Subscription } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { TeamAssignmentPayload } from 'app/entities/team.model';
import { participationStatus } from 'app/exercises/shared/exercise/exercise.utils';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import dayjs from 'dayjs/esm';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { faCircleNotch, faSync } from '@fortawesome/free-solid-svg-icons';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { BarControlConfiguration, BarControlConfigurationProvider } from 'app/overview/tab-bar/tab-bar';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';

@Component({
    selector: 'jhi-course-overview',
    templateUrl: './course-overview.component.html',
    styleUrls: ['course-overview.scss', './tab-bar/tab-bar.scss'],
})
export class CourseOverviewComponent implements OnInit, OnDestroy, AfterViewInit {
    private courseId: number;
    private subscription: Subscription;
    public course?: Course;
    public refreshingCourse = false;
    private teamAssignmentUpdateListener: Subscription;
    private quizExercisesChannel: string;

    // Rendered embedded view for controls in the bar so we can destroy it if needed
    private controlsEmbeddedView?: EmbeddedViewRef<any>;
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

    constructor(
        private courseService: CourseManagementService,
        private courseExerciseService: CourseExerciseService,
        private courseCalculationService: CourseScoreCalculationService,
        private learningGoalService: LearningGoalService,
        private route: ActivatedRoute,
        private teamService: TeamService,
        private jhiWebsocketService: JhiWebsocketService,
        private serverDateService: ArtemisServerDateService,
        private alertService: AlertService,
        private changeDetectorRef: ChangeDetectorRef,
        private profileService: ProfileService,
    ) {}

    async ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.course = this.courseCalculationService.getCourse(this.courseId);
        if (!this.course) {
            this.loadCourse();
        } else if (!this.course.learningGoals || !this.course.prerequisites) {
            // If the course is present but without learning goals (e.g. loaded in Artemis overview), we only need to fetch those
            this.loadLearningGoals();
        }
        await this.subscribeToTeamAssignmentUpdates();
        this.subscribeForQuizChanges();
    }

    ngAfterViewInit() {
        // Check if controls mount point is available, if not, wait for it
        if (this.controlsViewContainer) {
            this.tryRenderControls();
        } else {
            this.vcSubscription = this.controlsViewContainerAsList.changes.subscribe(() => this.tryRenderControls());
        }
    }

    /**
     * Accepts a component reference of the subcomponent rendered based on the current route.
     * If it provides a controlsConfiguration, we try to render the controls component
     * @param componentRef the sub route component that has been mounted into the router outlet
     */
    onSubRouteActivate(componentRef: any) {
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
     * Fetch the course from the server including all exercises, lectures, exams and learning goals
     * @param refresh Whether this is a force refresh (displays loader animation)
     */
    loadCourse(refresh = false) {
        this.refreshingCourse = refresh;
        this.courseService.findOneForDashboard(this.courseId).subscribe({
            next: (res: HttpResponse<Course>) => {
                this.courseCalculationService.updateCourse(res.body!);
                this.course = this.courseCalculationService.getCourse(this.courseId);
                setTimeout(() => (this.refreshingCourse = false), 500); // ensure min animation duration
            },
            error: (error: HttpErrorResponse) => {
                const errorMessage = error.headers.get('X-artemisApp-message')!;
                this.alertService.addAlert({
                    type: AlertType.DANGER,
                    message: errorMessage,
                    disableTranslation: true,
                });
            },
        });
    }

    ngOnDestroy() {
        if (this.teamAssignmentUpdateListener) {
            this.teamAssignmentUpdateListener.unsubscribe();
        }
        if (this.quizExercisesChannel) {
            this.jhiWebsocketService.unsubscribe(this.quizExercisesChannel);
        }
        this.controlsSubscription?.unsubscribe();
        this.vcSubscription?.unsubscribe();
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

    loadLearningGoals() {
        forkJoin([this.learningGoalService.getAllForCourse(this.courseId), this.learningGoalService.getAllPrerequisitesForCourse(this.courseId)]).subscribe({
            next: ([learningGoals, prerequisites]) => {
                if (this.course) {
                    this.course.learningGoals = learningGoals.body!;
                    this.course.prerequisites = prerequisites.body!;
                    this.courseCalculationService.updateCourse(this.course);
                }
            },
            error: () => {},
        });
    }

    /**
     * check if there is at least one exam which should be shown
     */
    hasVisibleExams(): boolean {
        for (const exam of this.course?.exams!) {
            if (exam.visibleDate && dayjs(exam.visibleDate).isBefore(this.serverDateService.now())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the course has any learning goals or prerequisites
     */
    hasLearningGoals(): boolean {
        return !!(this.course?.learningGoals?.length || this.course?.prerequisites?.length);
    }

    /**
     * Receives team assignment changes and updates related attributes of the affected exercise
     */
    async subscribeToTeamAssignmentUpdates() {
        this.teamAssignmentUpdateListener = (await this.teamService.teamAssignmentUpdates).subscribe((teamAssignment: TeamAssignmentPayload) => {
            const exercise = this.course?.exercises?.find((courseExercise) => courseExercise.id === teamAssignment.exerciseId);
            if (exercise) {
                exercise.studentAssignedTeamId = teamAssignment.teamId;
                exercise.studentParticipations = teamAssignment.studentParticipations;
                exercise.participationStatus = participationStatus(exercise);
            }
        });
    }
}
