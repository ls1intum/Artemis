import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StudentExam } from 'app/entities/student-exam.model';

@Component({
    selector: 'jhi-student-exam-summary',
    template: '<jhi-exam-participation-summary [studentExam]="studentExam" [instructorView]="true"></jhi-exam-participation-summary>',
})
export class StudentExamSummaryComponent implements OnInit {
    studentExam: StudentExam;

    constructor(private route: ActivatedRoute) {}

    /**
     * Initialize the studentExam
     */
    ngOnInit(): void {
        this.route.data.subscribe(({ studentExam: studentExamWithGrade }) => (this.studentExam = studentExamWithGrade.studentExam));
    }
}
