import { Component, Input } from '@angular/core';
import { ProgrammingExerciseUpdateService } from 'app/exercises/programming/manage/update/programming-exercise-update.service';

@Component({
    selector: 'jhi-programming-exercise-info',
    templateUrl: './programming-exercise-information.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseInformationComponent {
    @Input() isExamMode: boolean;

    constructor(public programmingExerciseUpdateService: ProgrammingExerciseUpdateService) {}
}
