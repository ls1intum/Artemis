import { Component, OnInit } from '@angular/core';
import { Course, CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { Exercise } from 'app/entities/exercise';

@Component({
    selector: 'jhi-course-grade-book',
    templateUrl: './course-grade-book.component.html',
    styleUrls: ['../course-overview.scss', './course-grade-book.scss']
})
export class CourseGradeBookComponent implements OnInit {
    private courseId: number;
    private subscription: Subscription;
    private course: Course;
    public exercises: Exercise[];

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
        this.courseService.findAllResultsOfCourseForExerciseAndCurrentUser(this.courseId).subscribe(course => {
            this.exercises = course.exercises;
        });
    }
}
