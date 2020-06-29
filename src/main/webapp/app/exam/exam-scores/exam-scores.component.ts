import { Component, OnInit, OnDestroy } from '@angular/core';

@Component({
    selector: 'jhi-exam-scores',
    templateUrl: './exam-scores.component.html',
    styles: [],
})
export class ExamScoresComponent implements OnInit {
    public examScoreDTO: any;
    public exerciseGroups: any[];
    public rows: any[];

    ngOnInit() {
        this.examScoreDTO = {
            exerciseGroups: [
                {
                    id: 1,
                    title: 'Text Exercises',
                },
                {
                    id: 2,
                    title: 'Modelling Exercises',
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
}
