import { Component, OnInit } from '@angular/core';
import { Course } from 'app/entities/course/course.model';
import { CourseService } from 'app/entities/course/course.service';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-course-overview',
    templateUrl: './course-exercises-overview.component.html'
})
export class CourseExercisesOverviewComponent implements OnInit {

    course: Course;
    courseId = 0;
    quizExercisesCount = 0;
    textExercisesCount = 0;
    programmingExercisesCount = 0;
    modelingExercisesCount = 0;
    fileUploadExercisesCount = 0;

    constructor(
        private courseService: CourseService,
        private route: ActivatedRoute,
    ) {
    }

    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.courseService.find(this.courseId).subscribe(courseResponse => this.course = courseResponse.body);
    }
}
