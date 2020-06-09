import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Exam } from 'app/entities/exam.model';

@Component({
    selector: 'jhi-exam-detail',
    templateUrl: './exam-detail.component.html',
})
export class ExamDetailComponent implements OnInit {
    exam: Exam;

    constructor(private route: ActivatedRoute) {}

    /**
     * Initialize the exam
     */
    ngOnInit(): void {}
}
