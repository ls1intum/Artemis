import { Component, OnInit } from '@angular/core';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { LearningGoalManagementService } from 'app/learning-goal/learning-goal-management/learning-goal-management.service';
import { AlertService } from 'app/core/alert/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { LearningGoal } from 'app/entities/learning-goal.model';
import { onError } from 'app/shared/util/global.utils';
import { concatMap, filter, map } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-learning-goal-connect',
    templateUrl: './learning-goal-connect.component.html',
    styles: [],
})
export class LearningGoalConnectComponent implements OnInit {
    private learningGoal: LearningGoal;
    private course: Course;
    /**
     * boolean symbolises if the exercise is connected to the learning goal
     */
    private exercises: [Exercise, boolean][] = [];

    private exercisesOfLearningGoal: Exercise[] = [];
    private exercisesOfCourse: Exercise[] = [];

    constructor(
        private activatedRoute: ActivatedRoute,
        private courseManagementService: CourseManagementService,
        private learningGoalManagementService: LearningGoalManagementService,
        private router: Router,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.paramMap
            .pipe(
                map((params) => params.get('goalid')),
                concatMap((goalId: string) => this.learningGoalManagementService.findById(+goalId)),
                filter((response: HttpResponse<LearningGoal>) => response.ok),
                concatMap((response: HttpResponse<LearningGoal>) => {
                    this.learningGoal = response.body!;
                    this.exercisesOfLearningGoal = this.learningGoal.exercises!;
                    return this.courseManagementService.findWithExercises(this.learningGoal.course?.id!);
                }),
                map((response: HttpResponse<Course>) => response.body!),
            )
            .subscribe(
                (course: Course) => {
                    this.course = course;
                    this.exercisesOfCourse = this.course.exercises!;
                    const associatedExerciseIds = this.exercisesOfLearningGoal.map((exercise) => exercise.id);
                    this.exercises = this.exercisesOfCourse.map((exercise: Exercise) => {
                        if (associatedExerciseIds.includes(exercise.id)) {
                            return [exercise, true];
                        } else {
                            return [exercise, false];
                        }
                    });
                },
                (err: HttpErrorResponse) => onError(this.alertService, err),
            );
    }
}
