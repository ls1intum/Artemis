import { Component, OnInit } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from './course-management.service';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-course-management-exams',
    templateUrl: './course-management-exams.component.html',
})
export class CourseManagementExamsComponent implements OnInit {
    course: Course;
    courseId = 0;

    constructor(private courseService: CourseManagementService, private route: ActivatedRoute) {}

    /**
     * initializes courseId and course
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.courseService.find(this.courseId).subscribe((courseResponse) => (this.course = courseResponse.body!));
    }
}
