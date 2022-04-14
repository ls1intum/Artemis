import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StudentExam } from 'app/entities/student-exam.model';
import { StudentExamWithGradeDTO } from 'app/exam/exam-scores/exam-score-dtos.model';

@Component({
    selector: 'jhi-student-exam-summary',
    template: '<jhi-exam-participation-summary [studentExamWithGrade]="studentExamWithGrade" [instructorView]="true"></jhi-exam-participation-summary>',
})
export class StudentExamSummaryComponent implements OnInit {
    studentExamWithGrade: StudentExamWithGradeDTO;

    constructor(private route: ActivatedRoute) {}

    /**
     * Initialize the Student Exam With Grade
     */
    ngOnInit(): void {
        this.route.data.subscribe(({ studentExam: studentExamWithGrade }) => (this.studentExamWithGrade = studentExamWithGrade));
    }
}
