import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { ExamMonitoringService } from 'app/exam/monitoring/exam-monitoring.service';
import { ExamActionService } from 'app/exam/monitoring/exam-action.service';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-monitoring-activity-log',
    templateUrl: './monitoring-students.component.html',
    styleUrls: ['./monitoring-students.component.scss'],
})
export class MonitoringStudentsComponent implements OnInit, OnDestroy {
    @ViewChild('dataTable') table: any;
    // Subscriptions
    private routeSubscription?: Subscription;
    private examSubscription?: Subscription;
    // Exam
    examId: number;
    courseId: number;
    exam: Exam;

    // Table columns
    readonly columns = [
        { prop: 'collapse', minWidth: 50, width: 50, maxWidth: 50 },
        { prop: 'id', minWidth: 150, width: 200, maxWidth: 200 },
    ];

    constructor(private route: ActivatedRoute, private examMonitoringService: ExamMonitoringService, public examActionService: ExamActionService) {}

    ngOnInit() {
        this.routeSubscription = this.route.parent?.params.subscribe((params) => {
            this.examId = Number(params['examId']);
            this.courseId = Number(params['courseId']);
        });

        this.examSubscription = this.examMonitoringService.getExamBehaviorSubject(this.examId)?.subscribe((exam) => {
            this.exam = exam!;
        });
    }

    ngOnDestroy() {
        this.examSubscription?.unsubscribe();
        this.routeSubscription?.unsubscribe();
    }

    /**
     * Toggle the visibility of the row.
     * @param exercise selected exercise
     */
    toggleExpandRow(exercise: Exercise) {
        this.table.rowDetail.toggleExpandRow(exercise);
    }

    /**
     * Event-Listener to receive the actions performed on the table.
     * @param event received event
     */
    onActivate(event: any) {
        if (event.type === 'click') {
            this.toggleExpandRow(event.row);
        }
    }
}
