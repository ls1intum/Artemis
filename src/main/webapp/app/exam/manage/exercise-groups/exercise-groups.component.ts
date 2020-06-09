import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-exercise-groups',
    templateUrl: './exercise-groups.component.html',
})
export class ExerciseGroupsComponent implements OnInit {
    examId: number;

    constructor(private route: ActivatedRoute) {}

    /**
     * Initialize the examId
     */
    ngOnInit(): void {
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
    }
}
