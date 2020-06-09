import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ExerciseGroup } from 'app/entities/exercise-group.model';

@Component({
    selector: 'jhi-exercise-group-detail',
    templateUrl: './exercise-group-detail.component.html',
})
export class ExerciseGroupDetailComponent implements OnInit {
    exerciseGroup: ExerciseGroup;

    constructor(private route: ActivatedRoute) {}

    /**
     * Initialize the exerciseGroup
     */
    ngOnInit(): void {}
}
