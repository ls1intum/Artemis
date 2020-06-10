import { Component, Input } from '@angular/core';
import { Router } from '@angular/router';
import { Exam } from 'app/entities/exam.model';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-course-exam-detail',
    templateUrl: './course-exam-detail.component.html',
    styleUrls: ['../course-exams.scss'],
})
export class CourseExamDetailComponent {
    @Input() exam: Exam;
    @Input() course: Course;

    constructor(private router: Router) {}

    openExam(): void {
        // TODO logic to navigate to summary if exam is over
        this.router.navigate(['courses', this.course.id, 'exam', this.exam.id, 'start'], {
            state: {
                exam: this.exam, // TODO: load student exam
            },
        });
    }
}
