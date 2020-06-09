import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-student-exams',
    templateUrl: './student-exams.component.html',
})
export class StudentExamsComponent implements OnInit {
    examId: number;

    constructor(private route: ActivatedRoute) {}

    /**
     * Initialize the examId
     */
    ngOnInit(): void {}
}
