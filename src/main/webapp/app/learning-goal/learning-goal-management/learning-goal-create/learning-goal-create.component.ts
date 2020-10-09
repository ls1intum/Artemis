import { Component, OnInit } from '@angular/core';
import { LearningGoal } from 'app/entities/learning-goal.model';
import { ActivatedRoute, Router } from '@angular/router';
import { filter, map, switchMap } from 'rxjs/operators';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { HttpResponse } from '@angular/common/http';
import { LearningGoalManagementService } from 'app/learning-goal/learning-goal-management/learning-goal-management.service';

@Component({
    selector: 'jhi-learning-goal-create',
    templateUrl: './learning-goal-create.component.html',
    styles: [],
})
export class LearningGoalCreateComponent implements OnInit {
    learningGoal: LearningGoal;

    constructor(
        private activatedRoute: ActivatedRoute,
        private courseManagementService: CourseManagementService,
        private learningGoalManagementService: LearningGoalManagementService,
        private router: Router,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.paramMap
            .pipe(
                map((params) => params.get('courseId')),
                switchMap((courseId: string) => this.courseManagementService.find(+courseId)),
                filter((response: HttpResponse<Course>) => response.ok),
                map((course: HttpResponse<Course>) => course.body!),
            )
            .subscribe((course: Course) => {
                const newLearningGoal = new LearningGoal();
                newLearningGoal.course = course;
                newLearningGoal.exercises = [];
                newLearningGoal.lectures = [];
                this.learningGoal = newLearningGoal;
            });
    }

    createLearningGoal(learningGoal: LearningGoal): void {
        this.learningGoalManagementService.createLearningGoal(learningGoal).subscribe(() => {
            this.router.navigate(['/course-management', this.learningGoal?.course?.id, 'goals']);
        });
    }
}
