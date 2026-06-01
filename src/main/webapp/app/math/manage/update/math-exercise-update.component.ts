import { Component, OnInit, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { MathExerciseService } from '../service/math-exercise.service';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { CategorySelectorComponent } from 'app/shared/category-selector/category-selector.component';
import { DifficultyPickerComponent } from 'app/exercise/difficulty-picker/difficulty-picker.component';
import { IncludedInOverallScorePickerComponent } from 'app/exercise/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';

@Component({
    selector: 'jhi-math-exercise-update',
    templateUrl: './math-exercise-update.component.html',
    imports: [
        FormsModule,
        TranslateDirective,
        CategorySelectorComponent,
        DifficultyPickerComponent,
        IncludedInOverallScorePickerComponent,
        MarkdownEditorMonacoComponent,
        FormDateTimePickerComponent,
        ArtemisTranslatePipe,
        ButtonModule,
        CardModule,
        CheckboxModule,
        InputTextModule,
        TextareaModule,
        TooltipModule,
    ],
})
export class MathExerciseUpdateComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private mathExerciseService = inject(MathExerciseService);
    private exerciseService = inject(ExerciseService);
    private router = inject(Router);

    mathExercise: MathExercise;
    isSaving: boolean;
    exerciseCategories = signal<ExerciseCategory[]>([]);
    existingCategories = signal<ExerciseCategory[]>([]);

    releaseDateField = viewChild<FormDateTimePickerComponent>('releaseDate');
    startDateField = viewChild<FormDateTimePickerComponent>('startDate');
    dueDateField = viewChild<FormDateTimePickerComponent>('dueDate');
    assessmentDateField = viewChild<FormDateTimePickerComponent>('assessmentDueDate');

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ mathExercise }) => {
            this.mathExercise = mathExercise;
            this.exerciseCategories.set(this.mathExercise.categories || []);
        });
    }

    updateCategories(categories: ExerciseCategory[]) {
        this.mathExercise.categories = categories;
    }

    validateDate() {
        this.exerciseService.validateDate(this.mathExercise);
    }

    save() {
        this.isSaving = true;
        if (this.mathExercise.id !== undefined) {
            this.mathExerciseService.update(this.mathExercise).subscribe({
                next: () => this.onSaveSuccess(),
                error: () => this.onSaveError(),
            });
        } else {
            this.mathExerciseService.create(this.mathExercise).subscribe({
                next: () => this.onSaveSuccess(),
                error: () => this.onSaveError(),
            });
        }
    }

    previousState() {
        this.router.navigate(['course-management', this.mathExercise.course?.id || this.activatedRoute.snapshot.params['courseId'], 'math-exercises']);
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
}
