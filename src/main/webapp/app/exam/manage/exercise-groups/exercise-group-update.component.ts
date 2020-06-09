import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ExerciseGroup } from 'app/entities/exercise-group.model';

@Component({
    selector: 'jhi-exercise-group-update',
    templateUrl: './exercise-group-update.component.html',
})
export class ExerciseGroupUpdateComponent implements OnInit {
    exerciseGroup: ExerciseGroup;

    constructor(private route: ActivatedRoute) {}

    /**
     * Initialize the exerciseGroup
     */
    ngOnInit(): void {}
}
