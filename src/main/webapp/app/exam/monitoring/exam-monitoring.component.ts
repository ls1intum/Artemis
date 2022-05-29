import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Exam } from 'app/entities/exam.model';
import { HttpResponse } from '@angular/common/http';
import { ExamMonitoringService } from './exam-monitoring.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

export class TableContent {
    translateValue: string;
    value: any;

    constructor(translateValue: string, value: any) {
        this.translateValue = translateValue;
        this.value = value;
    }
}

@Component({
    selector: 'jhi-exam-monitoring',
    templateUrl: './exam-monitoring.component.html',
    styleUrls: ['./exam-monitoring.component.scss', '../../overview/tab-bar/tab-bar.scss'],
})
export class ExamMonitoringComponent implements OnInit, OnDestroy {
    // 'overview', 'exercises', 'students', 'submissions', 'sessions', 'activity-log', 'summary'
    readonly sections: string[] = ['overview', 'exercises', 'activity-log'];

    // table
    table: TableContent[] = [];

    // Subscriptions
    private routeSubscription?: Subscription;

    private examId: number;
    private courseId: number;

    exam: Exam;

    constructor(
        private route: ActivatedRoute,
        private examManagementService: ExamManagementService,
        private examMonitoringService: ExamMonitoringService,
        private artemisDataPipe: ArtemisDatePipe,
    ) {}

    ngOnInit(): void {
        this.routeSubscription = this.route.params.subscribe((params) => {
            this.examId = parseInt(params['examId'], 10);
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.examManagementService.find(this.courseId, this.examId, false, true).subscribe((examResponse: HttpResponse<Exam>) => {
            this.exam = examResponse.body!;
            this.examMonitoringService.notifyExamSubscribers(this.exam);

            this.initTable();
        });
    }

    /**
     * Initialize the exam details table.
     * @private
     */
    private initTable() {
        const title = new TableContent('title', this.exam.title);
        const start = new TableContent('start', this.artemisDataPipe.transform(this.exam.startDate));
        const end = new TableContent('end', this.artemisDataPipe.transform(this.exam.endDate));
        const students = new TableContent('students', this.exam.numberOfRegisteredUsers);

        let amountOfExercises = 0;
        this.exam.exerciseGroups?.forEach((group) => (amountOfExercises += group.exercises?.length ?? 0));
        const exercises = new TableContent('exercises', amountOfExercises);

        const exerciseGroups = new TableContent('exerciseGroups', this.exam.exerciseGroups?.length);

        this.table.push(title, start, end, students, exercises, exerciseGroups);
    }

    ngOnDestroy(): void {
        this.routeSubscription?.unsubscribe();
    }
}
