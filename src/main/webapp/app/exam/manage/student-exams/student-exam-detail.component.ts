import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StudentExam } from 'app/entities/student-exam.model';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';

@Component({
    selector: 'jhi-student-exam-detail',
    templateUrl: './student-exam-detail.component.html',
})
export class StudentExamDetailComponent implements OnInit {
    studentExam: StudentExam;

    constructor(private route: ActivatedRoute, private studentExamService: StudentExamService) {}

    /**
     * Initialize the studentExam
     */
    ngOnInit(): void {}
}
