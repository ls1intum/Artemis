import { Component, OnInit } from '@angular/core';
import { Course, CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { HttpResponse } from '@angular/common/http';
import { Exercise } from 'app/entities/exercise';

@Component({
    selector: 'jhi-course-exercises',
    templateUrl: './course-exercises.component.html',
    styleUrls: ['../course-overview.scss']
})
export class CourseExercisesComponent implements OnInit {
    private courseId: number;
    private subscription: Subscription;
    course: Course;
    weeklyExercisesGrouped: Exercise[];

    constructor(
        private courseService: CourseService,
        private courseCalculationService: CourseScoreCalculationService,
        private courseServer: CourseService,
        private route: ActivatedRoute) {
    }

    ngOnInit() {
        this.subscription = this.route.parent.params.subscribe(params => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.course = this.courseCalculationService.getCourse(this.courseId);
        if (this.course === undefined) {
            this.courseService.findAll().subscribe((res: HttpResponse<Course[]>) => {
                this.courseCalculationService.setCourses(res.body);
                this.course = this.courseCalculationService.getCourse(this.courseId);
            });
        }
        this.groupExercises();
    }

    private groupExercises(): void {
        let groupedExercises: Exercise[] = [];
        this.course.exercises.forEach(exercise => {
            let weekIndex = exercise.dueDate.week().toString();
            if (typeof groupedExercises[weekIndex] !== 'object') groupedExercises[weekIndex] = [];
            groupedExercises[weekIndex].push(exercise);
        });
        // removing empty array elements
        groupedExercises = groupedExercises.filter(el => !!el);
        this.weeklyExercisesGrouped = groupedExercises;
    }

}
