import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseUpdateService } from 'app/exercises/programming/manage/update/programming-exercise-update.service';

@Component({
    selector: 'jhi-programming-exercise-info',
    templateUrl: './programming-exercise-information.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseInformationComponent {
    @Input() isImport: boolean;
    @Input() isExamMode: boolean;
    @Input() programmingExercise: ProgrammingExercise;

    constructor(public programmingExerciseUpdateService: ProgrammingExerciseUpdateService) {}
}
