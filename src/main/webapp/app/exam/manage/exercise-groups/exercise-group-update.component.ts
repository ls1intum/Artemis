import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';

@Component({
    selector: 'jhi-exercise-group-update',
    templateUrl: './exercise-group-update.component.html',
})
export class ExerciseGroupUpdateComponent implements OnInit {
    exerciseGroup: ExerciseGroup;

    constructor(private route: ActivatedRoute, private exerciseGroupService: ExerciseGroupService) {}

    /**
     * Initialize the exerciseGroup
     */
    ngOnInit(): void {}
}
