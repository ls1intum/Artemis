import { Component, OnInit } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from '../course/manage/course-management.service';
import { HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/alert/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { courseOverviewTour } from 'app/guided-tour/tours/course-overview-tour';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import * as moment from 'moment';

@Component({
    selector: 'jhi-overview',
    templateUrl: './courses.component.html',
    styles: [],
})
export class CoursesComponent implements OnInit {
    public courses: Course[];
    public nextRelevantCourse: Course;

    courseForGuidedTour: Course | null;

    constructor(
        private courseService: CourseManagementService,
        private exerciseService: ExerciseService,
        private jhiAlertService: AlertService,
        private accountService: AccountService,
        private courseScoreCalculationService: CourseScoreCalculationService,
        private guidedTourService: GuidedTourService,
        private teamService: TeamService,
    ) {}

    async ngOnInit() {
        this.loadAndFilterCourses();
        (await this.teamService.teamAssignmentUpdates).subscribe();
    }

    loadAndFilterCourses() {
        this.courseService.findAllForDashboard().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body!;
                this.courseScoreCalculationService.setCourses(this.courses);
                this.courseForGuidedTour = this.guidedTourService.enableTourForCourseOverview(this.courses, courseOverviewTour, true);
            },
            (response: string) => this.onError(response),
        );
    }

    private onError(error: string) {
        this.jhiAlertService.error('error.unexpectedError', { error }, undefined);
    }

    get nextRelevantExercise(): Exercise | undefined {
        const relevantExercises = new Array<Exercise>();
        let relevantExercise: Exercise | null = null;
        if (this.courses) {
            this.courses.forEach((course) => {
                const relevantExerciseForCourse = this.exerciseService.getNextExerciseForHours(course.exercises);
                if (relevantExerciseForCourse) {
                    relevantExerciseForCourse.course = course;
                    relevantExercises.push(relevantExerciseForCourse);
                }
            });
            if (relevantExercises.length === 0) {
                return undefined;
            } else if (relevantExercises.length === 1) {
                relevantExercise = relevantExercises[1];
            } else {
                relevantExercise =
                    // 1st priority is an active quiz
                    relevantExercises.find((exercise: QuizExercise) => exercise.isActiveQuiz) ||
                    // 2nd priority is a visible quiz
                    relevantExercises.find((exercise: QuizExercise) => exercise.isVisibleBeforeStart) ||
                    // 3rd priority is the next due exercise
                    relevantExercises.sort((a, b) => {
                        return moment(a.dueDate).valueOf() - moment(b.dueDate).valueOf();
                    })[0];
            }
            this.nextRelevantCourse = relevantExercise.course!;
            return relevantExercise;
        }
    }
}
