import { Component, OnInit } from '@angular/core';
import { ExerciseImportComponent } from 'app/exercises/shared/manage/exercise-import.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExercisePagingService } from 'app/exercises/programming/manage/services/programming-exercise-paging.service';
import { SortService } from 'app/shared/service/sort.service';

export enum TableColumn {
    ID = 'ID',
    TITLE = 'TITLE',
    PROGRAMMING_LANGUAGE = 'PROGRAMMING_LANGUAGE',
    COURSE_TITLE = 'COURSE_TITLE',
}

@Component({
    selector: 'jhi-programming-exercise-import',
    templateUrl: './programming-exercise-import.component.html',
})
export class ProgrammingExerciseImportComponent extends ExerciseImportComponent<ProgrammingExercise> implements OnInit {
    readonly column = TableColumn;

    constructor(private pagingService: ProgrammingExercisePagingService, sortService: SortService, activeModal: NgbActiveModal) {
        super(sortService, activeModal);
    }

    ngOnInit() {
        this.init(this.pagingService);
    }
}
