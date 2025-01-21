import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamResultSummaryComponent } from '../../participate/summary/exam-result-summary.component';

@Component({
    selector: 'jhi-student-exam-summary',
    template: '<jhi-exam-participation-summary [studentExam]="studentExam" [instructorView]="true" />',
    imports: [ExamResultSummaryComponent],
})
export class StudentExamSummaryComponent implements OnInit {
    private route = inject(ActivatedRoute);

    studentExam: StudentExam;

    /**
     * Initialize the studentExam
     */
    ngOnInit(): void {
        this.route.data.subscribe(({ studentExam: studentExamWithGrade }) => (this.studentExam = studentExamWithGrade.studentExam));
    }
}
