import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';

@Component({
    selector: 'jhi-student-exams',
    templateUrl: './student-exams.component.html',
})
export class StudentExamsComponent implements OnInit {
    examId: number;

    constructor(private route: ActivatedRoute, private studentExamService: StudentExamService) {}

    /**
     * Initialize the examId
     */
    ngOnInit(): void {
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
    }
}
