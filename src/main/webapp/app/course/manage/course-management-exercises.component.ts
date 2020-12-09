import { Component, OnInit } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from './course-management.service';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-course-management-exercises',
    templateUrl: './course-management-exercises.component.html',
})
export class CourseManagementExercisesComponent implements OnInit {
    course: Course;
    courseId = 0;
    quizExercisesCount = 0;
    textExercisesCount = 0;
    programmingExercisesCount = 0;
    modelingExercisesCount = 0;
    fileUploadExercisesCount = 0;

    constructor(private courseService: CourseManagementService, private route: ActivatedRoute) {}

    /**
     * initializes courseId and course
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.parent!.snapshot.paramMap.get('courseId'));
        this.courseService.find(this.courseId).subscribe((courseResponse) => (this.course = courseResponse.body!));
    }
}
