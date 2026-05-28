import { Component, input } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { HelpIconComponent } from 'app/ui/components/help-icon/help-icon.component';

@Component({
    selector: 'jhi-programming-exercise-version-control',
    templateUrl: './programming-exercise-version-control.component.html',
    imports: [TranslateDirective, ReactiveFormsModule, FormsModule, HelpIconComponent],
})
export class ProgrammingExerciseVersionControlComponent {
    programmingExercise = input.required<ProgrammingExercise>();
    isEditFieldDisplayedRecord = input.required<Record<ProgrammingExerciseInputField, boolean>>();
}
