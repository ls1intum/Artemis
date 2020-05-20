import { Component, OnInit, OnDestroy } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from '../course/manage/course-management.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { HttpResponse } from '@angular/common/http';
import { isOrion } from 'app/shared/orion/orion';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { TeamAssignmentPayload } from 'app/entities/team.model';
import { participationStatus } from 'app/exercises/shared/exercise/exercise-utils';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';

const DESCRIPTION_READ = 'isDescriptionRead';

@Component({
    selector: 'jhi-course-overview',
    templateUrl: './course-overview.component.html',
    styleUrls: ['course-overview.scss'],
})
export class CourseOverviewComponent implements OnInit, OnDestroy {
    readonly isOrion = isOrion;
    CachingStrategy = CachingStrategy;
    private courseId: number;
    private subscription: Subscription;
    public course: Course | null;
    public refreshingCourse = false;
    public courseDescription: string;
    public enableShowMore: boolean;
    public longTextShown: boolean;
    private teamAssignmentUpdateListener: Subscription;
    private quizExercisesChannel: string;

    constructor(
        private courseService: CourseManagementService,
        private courseCalculationService: CourseScoreCalculationService,
        private route: ActivatedRoute,
        private teamService: TeamService,
        private jhiWebsocketService: JhiWebsocketService,
    ) {}

    /**
     * On init, gets the course id, loads the course from the server {@see loadCourse},
     * adjusts its description {@see adjustCourseDescription} and
     * subscribe for the team assignment updates {@see subscribeToTeamAssignmentUpdates} and quiz changes {@see subscribeForQuizChanges}
     */
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

    /**
     * Gets the course with the given course id from the server {@see findOneForDashboard} and runs an animation at the same time
     * based on a timeout
     * @param refresh - boolean variable to set if the 'refresh' button is pressed
     */
    loadCourse(refresh = false) {
        this.refreshingCourse = refresh;
        this.courseService.findOneForDashboard(this.courseId).subscribe((res: HttpResponse<Course>) => {
            this.courseCalculationService.updateCourse(res.body!);
            this.course = this.courseCalculationService.getCourse(this.courseId);
            this.adjustCourseDescription();
            setTimeout(() => (this.refreshingCourse = false), 500); // ensure min animation duration
        });
    }

    /**
     * On destroy, unsubscribe from observables
     */
    ngOnDestroy() {
        if (this.teamAssignmentUpdateListener) {
            this.teamAssignmentUpdateListener.unsubscribe();
        }
        if (this.quizExercisesChannel) {
            this.jhiWebsocketService.unsubscribe(this.quizExercisesChannel);
        }
    }

    /**
     * subscribes for the quizzes that are going to be visible
     */
    subscribeForQuizChanges() {
        // subscribe to quizzes which get visible
        if (!this.quizExercisesChannel) {
            this.quizExercisesChannel = '/topic/' + this.courseId + '/quizExercises';

            // quizExercise channel => react to changes made to quizExercise (e.g. start date)
            this.jhiWebsocketService.subscribe(this.quizExercisesChannel);
            this.jhiWebsocketService.receive(this.quizExercisesChannel).subscribe(
                (quizExercise) => {
                    // the quiz was set to visible, we should add it to the exercise list and display it at the top
                    if (this.course) {
                        this.course.exercises = this.course.exercises.filter((exercise) => exercise.id !== quizExercise.id);
                        this.course.exercises.push(quizExercise);
                        // this lines makes sure the quiz is sorted correctly
                        this.courseService.courseWasUpdated(this.course);
                    }
                },
                () => {},
            );
        }
    }

    /**
     * Adjusts shown course description according to description length
     */
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

    /**
     * Shows long description of the course
     */
    showLongDescription() {
        this.courseDescription = this.course!.description;
        this.longTextShown = true;
    }

    /**
     * Shows short description of the course
     */
    showShortDescription() {
        this.courseDescription = this.course!.description.substr(0, 50) + '...';
        this.longTextShown = false;
    }

    /**
     * Receives team assignment changes and updates related attributes of the affected exercise
     */
    async subscribeToTeamAssignmentUpdates() {
        this.teamAssignmentUpdateListener = (await this.teamService.teamAssignmentUpdates).subscribe((teamAssignment: TeamAssignmentPayload) => {
            const exercise = this.course!.exercises.find((courseExercise) => courseExercise.id === teamAssignment.exerciseId);
            if (exercise) {
                exercise.studentAssignedTeamId = teamAssignment.teamId;
                exercise.studentParticipations = teamAssignment.studentParticipations;
                exercise.participationStatus = participationStatus(exercise);
            }
        });
    }
}
