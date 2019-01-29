import { Component, OnInit } from '@angular/core';
import { Course, CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { ResultService } from "app/entities/result";
import { Exercise } from "app/entities/exercise";

@Component({
    selector: 'jhi-course-gradebook',
    templateUrl: './course-gradebook.component.html',
    styleUrls: ['../course-overview.scss', './course-gradebook.scss']
})
export class CourseGradeBookComponent implements OnInit {
    private courseId: number;
    private subscription: Subscription;
    private course: Course;
    private exercises: Exercise[];

    constructor(
        private courseService: CourseService,
        private resultService: ResultService,
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
        this.courseService.findAllResultsOfCourseForExerciseAndCurrentUser(this.courseId).subscribe((course) => {
            this.exercises = course.exercises;
        });
    }
}
