import { Component, OnInit } from '@angular/core';
import { ExerciseImportComponent, TableColumn } from 'app/exercises/shared/manage/exercise-import.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { SortService } from 'app/shared/service/sort.service';
import { QuizExercisePagingService } from 'app/exercises/quiz/manage/quiz-exercise-paging.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

@Component({
    selector: 'jhi-quiz-exercise-import',
    templateUrl: './quiz-exercise-import.component.html',
})
export class QuizExerciseImportComponent extends ExerciseImportComponent<QuizExercise> implements OnInit {
    readonly column = TableColumn;

    constructor(private pagingService: QuizExercisePagingService, sortService: SortService, activeModal: NgbActiveModal) {
        super(sortService, activeModal);
    }

    ngOnInit(): void {
        super.init(this.pagingService);
    }
}
