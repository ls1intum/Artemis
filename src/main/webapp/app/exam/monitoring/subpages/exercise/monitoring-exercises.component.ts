import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { ExamMonitoringService } from 'app/exam/monitoring/exam-monitoring.service';
import { ExamActionService } from 'app/exam/monitoring/exam-action.service';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-monitoring-exercises',
    templateUrl: './monitoring-exercises.component.html',
    styleUrls: ['./monitoring-exercises.component.scss'],
})
export class MonitoringExercisesComponent implements OnInit, OnDestroy {
    @ViewChild('dataTable') table: any;
    // Subscriptions
    private routeSubscription?: Subscription;
    private examSubscription?: Subscription;
    // Exam
    examId: number;
    courseId: number;
    exam: Exam;
    exercises: Exercise[] = [];

    // Table columns
    readonly columns = [
        { prop: 'collapse', minWidth: 50, width: 50, maxWidth: 50 },
        { prop: 'id', minWidth: 50, width: 100, maxWidth: 100 },
        { prop: 'exerciseGroup.id', minWidth: 150, width: 150, maxWidth: 200 },
        { prop: '_title', minWidth: 150, width: 150 },
        { prop: 'type', minWidth: 150, width: 150 },
    ];

    constructor(private route: ActivatedRoute, private examMonitoringService: ExamMonitoringService, public examActionService: ExamActionService) {}

    ngOnInit() {
        this.routeSubscription = this.route.parent?.params.subscribe((params) => {
            this.examId = Number(params['examId']);
            this.courseId = Number(params['courseId']);
        });

        this.examSubscription = this.examMonitoringService.getExamBehaviorSubject(this.examId)?.subscribe((exam) => {
            this.exam = exam!;
            this.exercises = this.exam
                .exerciseGroups!.map((group) => {
                    const exercises = group.exercises;
                    exercises?.forEach((exercise) => {
                        exercise.exerciseGroup = group;
                    });
                    return exercises;
                })
                .filter((exercise) => !!exercise)
                .flat() as Exercise[];
        });
    }

    ngOnDestroy() {
        this.examSubscription?.unsubscribe();
        this.routeSubscription?.unsubscribe();
    }

    toggleExpandRow(exercise: Exercise) {
        this.table.rowDetail.toggleExpandRow(exercise);
    }

    onActivate(event: any) {
        if (event.type === 'click') {
            this.toggleExpandRow(event.row);
        }
    }

    prepareProp(prop: string) {
        if (prop.startsWith('_')) {
            return prop.substring(1);
        }
        return prop;
    }
}
