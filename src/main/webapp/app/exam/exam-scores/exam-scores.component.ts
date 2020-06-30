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
            this.examService.getExamScore(params['courseId'], params['examId']).subscribe((examResponse) => {
                this.examScoreDTO = examResponse.body!;
                if (this.examScoreDTO) {
                    this.rows = this.examScoreDTO.students;
                    this.exerciseGroups = this.examScoreDTO.exerciseGroups;
                }
            });
        });
    }

    calculatePoints(row: any, exerciseGroup: any) {
        return (row.exerciseGroupToExerciseResult[exerciseGroup.id].exerciseAchievedScore * row.exerciseGroupToExerciseResult[exerciseGroup.id].exerciseMaxScore) / 100;
    }

    calculateOverallPoints(row: any) {
        let overall = 0;
        for (let i = 0; i < this.exerciseGroups.length; i++) {
            if (row.exerciseGroupToExerciseResult[this.exerciseGroups[i].id]) {
                overall += this.calculatePoints(row, this.exerciseGroups[i]);
            }
        }
        return overall;
    }

    calculateTotalAverage() {
        let sum = 0;
        for (let i = 0; i < this.rows.length; i++) {
            sum += this.calculateOverallPoints(this.rows[i]);
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
