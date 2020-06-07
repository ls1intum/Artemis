import { Component, OnInit } from '@angular/core';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-exam-participation',
    templateUrl: './exam-participation.component.html',
    styleUrls: ['./exam-participation.scss'],
})
export class ExamParticipationComponent implements OnInit {
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
