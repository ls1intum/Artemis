import { Component, OnInit, OnDestroy } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { SortService } from 'app/shared/service/sort.service';

@Component({
    selector: 'jhi-exam-scores',
    templateUrl: './exam-scores.component.html',
    styles: [],
})
export class ExamScoresComponent implements OnInit, OnDestroy {
    public examScoreDTO: any;
    public exerciseGroups: any[];
    public rows: any[];

    paramSub: Subscription;
    exam: Exam;
    predicate: string;
    reverse: boolean;

    constructor(private route: ActivatedRoute, private examService: ExamManagementService, private sortService: SortService) {
        this.predicate = 'id';
        this.reverse = false;
    }

    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.examService.find(params['courseId'], params['examId']).subscribe((examResponse) => {
                this.exam = examResponse.body!;
            });
        });

        this.examScoreDTO = {
            exerciseGroups: [
                {
                    id: 1,
                    title: 'Text Exercises',
                    maxPoints: 15,
                },
                {
                    id: 2,
                    title: 'Modelling Exercises',
                    maxPoints: 10,
                },
            ],
            results: [
                {
                    username: 'student1',
                    registrationNumber: '1234',
                    overallPoints: 20,
                    result: {
                        1: 12,
                    },
                },
            ],
        };
        this.rows = this.examScoreDTO.results;
        this.exerciseGroups = this.examScoreDTO.exerciseGroups;
    }

    calculateTotalAverage() {
        let sum = 0;
        for (let i = 0; i < this.rows.length; i++) {
            sum += this.rows[i].overallPoints;
        }

        return sum / this.rows.length;
    }

    sortRows() {
        this.sortService.sortByProperty(this.examScoreDTO.results, this.predicate, this.reverse);
    }

    exportResults() {
        // TODO
    }

    /**
     * Unsubscribes from all subscriptions
     */
    ngOnDestroy() {
        this.paramSub.unsubscribe();
    }
}
