import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-exam-management',
    templateUrl: './exam-management.component.html',
    styleUrls: ['./exam-management.scss'],
})
export class ExamManagementComponent implements OnInit {
    courseId = 0;

    constructor(private route: ActivatedRoute) {}

    /**
     * Initialize the courseId
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
    }
}
