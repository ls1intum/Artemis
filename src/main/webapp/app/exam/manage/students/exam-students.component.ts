import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-exam-students',
    templateUrl: './exam-students.component.html',
})
export class ExamStudentsComponent implements OnInit {
    examId: number;

    constructor(private route: ActivatedRoute) {}

    /**
     * Initialize the examId
     */
    ngOnInit(): void {}
}
