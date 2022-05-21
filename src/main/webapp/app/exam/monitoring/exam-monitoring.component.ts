import { AfterViewInit, Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Exam } from 'app/entities/exam.model';
import { HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-exam-monitoring',
    templateUrl: './exam-monitoring.component.html',
    styleUrls: ['./exam-monitoring.component.scss', '../../overview/tab-bar/tab-bar.scss'],
})
export class ExamMonitoringComponent implements OnInit, OnDestroy, AfterViewInit {
    // 'overview', 'exercises', 'students', 'submissions', 'sessions', 'activity-log', 'summary'
    readonly sections: string[] = ['overview', 'exercises'];

    private subscription: Subscription;
    private examId: number;
    private courseId: number;

    exam: Exam;

    constructor(private route: ActivatedRoute, private examManagementService: ExamManagementService) {}

    ngOnInit(): void {
        this.subscription = this.route.params.subscribe((params) => {
            this.examId = parseInt(params['examId'], 10);
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.examManagementService.find(this.courseId, this.examId).subscribe((examResponse: HttpResponse<Exam>) => {
            this.exam = examResponse.body!;
        });
    }

    ngOnDestroy(): void {
        this.subscription?.unsubscribe();
    }

    ngAfterViewInit() {}
}
