import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { Subject } from 'rxjs';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-programming-exercise-build-details',
    templateUrl: './programming-exercise-build-details.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
    standalone: true,
    imports: [ArtemisSharedModule],
})
export class ProgrammingExerciseBuildDetailsComponent {
    formValid: boolean;
    formValidChanges = new Subject<boolean>();

    @Input() isLocal: boolean;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;
    @Output() exerciseChange = new EventEmitter<ProgrammingExercise>();
    @Output() problemStatementChange = new EventEmitter<string>();
}
