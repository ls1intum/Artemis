import { Component, Input } from '@angular/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CreateProgrammingButtonComponent } from 'app/programming/manage/create-buttons/create-button/create-programming-button.component';
import { ImportProgrammingButtonComponent } from 'app/programming/manage/create-buttons/import-button/import-programming-button.component';

@Component({
    selector: 'jhi-programming-exercise-create-buttons',
    templateUrl: './programming-exercise-create-buttons.component.html',
    imports: [CreateProgrammingButtonComponent, ImportProgrammingButtonComponent],
})
export class ProgrammingExerciseCreateButtonsComponent {
    @Input()
    course: Course;
}
