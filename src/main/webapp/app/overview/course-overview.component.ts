import { Component, OnInit } from '@angular/core';
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
import { AlertService } from 'app/core/alert/alert.service';
import { participationStatus } from 'app/exercises/shared/exercise/exercise-utils';

const DESCRIPTION_READ = 'isDescriptionRead';

@Component({
    selector: 'jhi-course-overview',
    templateUrl: './course-overview.component.html',
    styleUrls: ['course-overview.scss'],
})
export class CourseOverviewComponent implements OnInit {
    readonly isOrion = isOrion;
    CachingStrategy = CachingStrategy;
    private courseId: number;
    private subscription: Subscription;
    public course: Course | null;
    public courseDescription: string;
    public enableShowMore: boolean;
    public longTextShown: boolean;

    constructor(
        private courseService: CourseManagementService,
        private courseCalculationService: CourseScoreCalculationService,
        private courseServer: CourseManagementService,
        private route: ActivatedRoute,
        private teamService: TeamService,
        private jhiAlertService: AlertService,
    ) {}

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.course = this.courseCalculationService.getCourse(this.courseId);
        if (!this.course) {
            this.courseService.findAll().subscribe((res: HttpResponse<Course[]>) => {
                this.courseCalculationService.setCourses(res.body!);
                this.course = this.courseCalculationService.getCourse(this.courseId);
                this.adjustCourseDescription();
            });
        }
        this.adjustCourseDescription();
        this.subscribeToTeamAssignmentUpdates();
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
        this.courseDescription = this.course!.description;
        this.longTextShown = true;
    }

    showShortDescription() {
        this.courseDescription = this.course!.description.substr(0, 50) + '...';
        this.longTextShown = false;
    }

    /**
     * Receives team assignment changes and updates related attributes of the affected exercise
     */
    subscribeToTeamAssignmentUpdates() {
        this.teamService.teamAssignmentUpdates.subscribe(
            (teamAssignment: TeamAssignmentPayload) => {
                const exercise = this.course!.exercises.find((courseExercise) => courseExercise.id === teamAssignment.exerciseId);
                if (exercise) {
                    exercise.studentAssignedTeamId = teamAssignment.teamId;
                    exercise.studentParticipations = teamAssignment.participation ? [teamAssignment.participation] : [];
                    exercise.participationStatus = participationStatus(exercise);
                }
            },
            (error) => this.onError(error),
        );
    }

    private onError(error: string) {
        this.jhiAlertService.error('error.unexpectedError', { error }, undefined);
    }
}
