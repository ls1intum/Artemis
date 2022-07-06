import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Exam } from 'app/entities/exam.model';
import { HttpResponse } from '@angular/common/http';
import { ExamMonitoringService } from './exam-monitoring.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import { ExamActionService } from './exam-action.service';

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
    private examMonitoringSubscription?: Subscription;
    private initialLoadSubscription?: Subscription;

    private examId: number;
    private courseId: number;

    // Exam Actions
    examActions: ExamAction[] = [];

    exam: Exam;

    constructor(
        private route: ActivatedRoute,
        private examManagementService: ExamManagementService,
        private examMonitoringService: ExamMonitoringService,
        private examActionService: ExamActionService,
        private artemisDataPipe: ArtemisDatePipe,
    ) {}

    ngOnInit() {
        this.routeSubscription = this.route.params.subscribe((params) => {
            this.examId = Number(params['examId']);
            this.courseId = Number(params['courseId']);
        });

        this.examManagementService.find(this.courseId, this.examId, false, true).subscribe((examResponse: HttpResponse<Exam>) => {
            this.exam = examResponse.body!;
            this.examMonitoringService.notifyExamSubscribers(this.exam);

            this.examMonitoringSubscription = this.examActionService.subscribeForLatestExamAction(this.exam!).subscribe((examAction) => {
                if (examAction) {
                    this.examActions.push(examAction);
                }
            });

            this.initialLoadSubscription = this.examActionService.loadInitialActions(this.exam).subscribe((examActions: ExamAction[]) => {
                examActions.forEach((action) => this.examActionService.notifyExamActionSubscribers(this.exam, action));
            });

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

        const exerciseGroups = new TableContent('exerciseGroups', this.exam.exerciseGroups?.length ?? 0);

        this.table.push(title, start, end, students, exercises, exerciseGroups);
    }

    ngOnDestroy() {
        this.routeSubscription?.unsubscribe();
        this.examActionService.unsubscribeForExamAction(this.exam!);
    }
}
