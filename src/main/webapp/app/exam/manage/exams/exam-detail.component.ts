import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Exam } from 'app/entities/exam.model';

@Component({
    selector: 'jhi-exam-update',
    templateUrl: './exam-update.component.html',
})
export class ExamDetailComponentComponent implements OnInit {
    exam: Exam;

    constructor(private route: ActivatedRoute) {}

    /**
     * Initialize the exam
     */
    ngOnInit(): void {}
}
