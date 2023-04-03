import { Component } from '@angular/core';
import { ProgrammingExerciseUpdateService } from 'app/exercises/programming/manage/update/programming-exercise-update.service';

@Component({
    selector: 'jhi-programming-exercise-difficulty',
    templateUrl: './programming-exercise-difficulty.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseDifficultyComponent {
    constructor(public programmingExerciseUpdateService: ProgrammingExerciseUpdateService) {}
}
