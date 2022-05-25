import { AfterViewInit, Component, OnChanges, OnDestroy, OnInit, SimpleChanges, Input } from '@angular/core';
import { Exam } from '../../../../entities/exam.model';
import { ExamManagementService } from '../../../manage/exam-management.service';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-monitoring-overview',
    templateUrl: './monitoring-overview.component.html',
    styleUrls: ['./monitoring-overview.component.scss', '../monitoring-card.component.scss'],
})
export class MonitoringOverviewComponent implements OnInit, OnChanges, OnDestroy, AfterViewInit {
    private subscription: Subscription;
    examId: number;
    courseId: number;
    exam: Exam;

    constructor(private route: ActivatedRoute, private examManagementService: ExamManagementService) {}

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.examId = parseInt(params['examId'], 10);
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.examManagementService.find(this.courseId, this.examId).subscribe((examResponse: HttpResponse<Exam>) => {
            this.exam = examResponse.body!;
        });
    }

    ngAfterViewInit(): void {}

    ngOnChanges(changes: SimpleChanges): void {
        throw new Error('Method not implemented.');
    }

    ngOnDestroy(): void {
        this.subscription.unsubscribe();
    }
}
