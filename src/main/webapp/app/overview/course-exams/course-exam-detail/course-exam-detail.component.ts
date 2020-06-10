import { Component, Input } from '@angular/core';
import { Exam } from 'app/entities/exam.model';

@Component({
    selector: 'jhi-course-exam-detail',
    templateUrl: './course-exam-detail.component.html',
    styleUrls: ['./course-exams.scss'],
})
export class CourseExamDetailComponent {
    @Input() exam: Exam;

    constructor() {}
}
