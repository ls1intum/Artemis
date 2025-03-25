import { Component, input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { InputFieldEditModeMapping } from 'app/programming/manage/update/programming-exercise-update.helper';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

@Component({
    selector: 'jhi-programming-exercise-version-control',
    templateUrl: './programming-exercise-version-control.component.html',
    imports: [TranslateDirective, ReactiveFormsModule, FormsModule, HelpIconComponent],
})
export class ProgrammingExerciseVersionControlComponent {
    programmingExercise = input.required<ProgrammingExercise>();
    isEditFieldDisplayedRecord = input.required<InputFieldEditModeMapping>();
}
