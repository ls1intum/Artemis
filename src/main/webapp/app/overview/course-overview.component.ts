import { Component, OnInit, OnDestroy } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CourseExerciseService, CourseManagementService } from '../course/manage/course-management.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { TeamAssignmentPayload } from 'app/entities/team.model';
import { participationStatus } from 'app/exercises/shared/exercise/exercise-utils';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import * as moment from 'moment';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { JhiAlertService } from 'ng-jhipster';

const DESCRIPTION_READ = 'isDescriptionRead';

@Component({
    selector: 'jhi-course-overview',
    templateUrl: './course-overview.component.html',
    styleUrls: ['course-overview.scss'],
})
export class CourseOverviewComponent implements OnInit, OnDestroy {
    CachingStrategy = CachingStrategy;
    private courseId: number;
    private subscription: Subscription;
    public course?: Course;
    public refreshingCourse = false;
    public courseDescription: string;
    public enableShowMore: boolean;
    public longTextShown: boolean;
    private teamAssignmentUpdateListener: Subscription;
    private quizExercisesChannel: string;

    constructor(
        private courseService: CourseManagementService,
        private courseExerciseService: CourseExerciseService,
        private courseCalculationService: CourseScoreCalculationService,
        private route: ActivatedRoute,
        private teamService: TeamService,
        private jhiWebsocketService: JhiWebsocketService,
        private serverDateService: ArtemisServerDateService,
        private jhiAlertService: JhiAlertService,
    ) {}

    async ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.course = this.courseCalculationService.getCourse(this.courseId);
        if (!this.course) {
            this.loadCourse();
        }
        this.adjustCourseDescription();
        await this.subscribeToTeamAssignmentUpdates();
        this.subscribeForQuizChanges();
    }

    loadCourse(refresh = false) {
        this.refreshingCourse = refresh;
        this.courseService.findOneForDashboard(this.courseId).subscribe(
            (res: HttpResponse<Course>) => {
                this.courseCalculationService.updateCourse(res.body!);
                this.course = this.courseCalculationService.getCourse(this.courseId);
                this.adjustCourseDescription();
                setTimeout(() => (this.refreshingCourse = false), 500); // ensure min animation duration
            },
            (error: HttpErrorResponse) => {
                const errorMessage = error.headers.get('X-artemisApp-message')!;
                const jhiAlert = this.jhiAlertService.error(errorMessage);
                jhiAlert.msg = errorMessage;
            },
        );
    }

    ngOnDestroy() {
        if (this.teamAssignmentUpdateListener) {
            this.teamAssignmentUpdateListener.unsubscribe();
        }
        if (this.quizExercisesChannel) {
            this.jhiWebsocketService.unsubscribe(this.quizExercisesChannel);
        }
    }

    subscribeForQuizChanges() {
        // subscribe to quizzes which get visible
        if (!this.quizExercisesChannel) {
            this.quizExercisesChannel = '/topic/courses/' + this.courseId + '/quizExercises';

            // quizExercise channel => react to changes made to quizExercise (e.g. start date)
            this.jhiWebsocketService.subscribe(this.quizExercisesChannel);
            this.jhiWebsocketService.receive(this.quizExercisesChannel).subscribe(
                (quizExercise: QuizExercise) => {
                    quizExercise = this.courseExerciseService.convertDateFromServer(quizExercise);
                    // the quiz was set to visible or started, we should add it to the exercise list and display it at the top
                    if (this.course && this.course.exercises) {
                        this.course.exercises = this.course.exercises.filter((exercise) => exercise.id !== quizExercise.id);
                        this.course.exercises.push(quizExercise);
                    }
                },
                () => {},
            );
        }
    }

    adjustCourseDescription() {
        if (this.course && this.course.description) {
            this.enableShowMore = this.course.description.length > 50;
            if (localStorage.getItem(DESCRIPTION_READ + this.course.shortName) && !this.courseDescription && this.enableShowMore) {
                this.showShortDescription();
            } else {
                this.showLongDescription();
                localStorage.setItem(DESCRIPTION_READ + this.course.shortName, 'true');
            }
        }
    }

    showLongDescription() {
        this.courseDescription = this.course?.description || '';
        this.longTextShown = true;
    }

    showShortDescription() {
        this.courseDescription = this.course?.description?.substr(0, 50) + '...' || '';
        this.longTextShown = false;
    }

    /**
     * check if there is at least one exam which should be shown
     */
    hasVisibleExams(): boolean {
        for (const exam of this.course?.exams!) {
            if (moment(exam.visibleDate).isBefore(this.serverDateService.now())) {
                return true;
            }
        }
        return false;
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
