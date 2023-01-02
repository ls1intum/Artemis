import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingExercisePagingService } from 'app/exercises/modeling/manage/modeling-exercise-paging.service';
import { ExerciseImportComponent, TableColumn } from 'app/exercises/shared/manage/exercise-import.component';
import { SortService } from 'app/shared/service/sort.service';

@Component({
    selector: 'jhi-modeling-exercise-import',
    templateUrl: './modeling-exercise-import.component.html',
})
export class ModelingExerciseImportComponent extends ExerciseImportComponent<ModelingExercise> implements OnInit {
    readonly column = TableColumn;

    constructor(private pagingService: ModelingExercisePagingService, sortService: SortService, activeModal: NgbActiveModal) {
        super(sortService, activeModal);
    }

    ngOnInit(): void {
        this.init(this.pagingService);
    }
}
