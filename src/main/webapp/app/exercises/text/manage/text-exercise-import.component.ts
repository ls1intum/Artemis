import { Component, OnInit } from '@angular/core';
import { ExerciseImportComponent, TableColumn } from 'app/exercises/shared/manage/exercise-import.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { SortService } from 'app/shared/service/sort.service';
import { TextExercisePagingService } from 'app/exercises/text/manage/text-exercise/text-exercise-paging.service';
import { TextExercise } from 'app/entities/text-exercise.model';

@Component({
    selector: 'jhi-text-exercise-import',
    templateUrl: './text-exercise-import.component.html',
})
export class TextExerciseImportComponent extends ExerciseImportComponent<TextExercise> implements OnInit {
    readonly column = TableColumn;

    constructor(private pagingService: TextExercisePagingService, sortService: SortService, activeModal: NgbActiveModal) {
        super(sortService, activeModal);
    }

    ngOnInit(): void {
        this.init(this.pagingService);
    }
}
