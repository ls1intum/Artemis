import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';

@Component({
    selector: 'jhi-exam-management',
    templateUrl: './exam-management.component.html',
    styleUrls: ['./exam-management.scss'],
})
export class ExamManagementComponent implements OnInit {
    courseId = 0;

    constructor(private route: ActivatedRoute, private examManagementService: ExamManagementService) {}

    /**
     * Initialize the courseId
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
    }
}
