import { Component, effect, inject, input } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { TextExerciseService } from '../service/text-exercise.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Router, RouterLink } from '@angular/router';
import { ExerciseComponent } from 'app/exercise/exercise.component';
import { onError } from 'app/shared/util/global.utils';
import { AccountService } from 'app/core/auth/account.service';
import { SortService } from 'app/shared/service/sort.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { AlertService } from 'app/shared/service/alert.service';
import { faPlus, faSort, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ExerciseImportWrapperComponent } from 'app/exercise/import/exercise-import-wrapper/exercise-import-wrapper.component';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { FormsModule } from '@angular/forms';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TextExerciseRowButtonsComponent } from '../row-buttons/text-exercise-row-buttons.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';

@Component({
    selector: 'jhi-text-exercise',
    templateUrl: './text-exercise.component.html',
    imports: [
        SortDirective,
        FormsModule,
        SortByDirective,
        TranslateDirective,
        FaIconComponent,
        RouterLink,
        ExerciseCategoriesComponent,
        TextExerciseRowButtonsComponent,
        DeleteButtonDirective,
        ArtemisDatePipe,
    ],
})
export class TextExerciseComponent extends ExerciseComponent {
    protected exerciseService = inject(ExerciseService); // needed in html code
    protected textExerciseService = inject(TextExerciseService); // needed in html code
    private router = inject(Router);
    private courseExerciseService = inject(CourseExerciseService);
    private modalService = inject(NgbModal);
    private alertService = inject(AlertService);
    private sortService = inject(SortService);
    private accountService = inject(AccountService);

    textExercises = input<TextExercise[]>([]);
    internalTextExercises: TextExercise[] = [];
    filteredTextExercises: TextExercise[] = [];

    // Icons
    faSort = faSort;
    faPlus = faPlus;
    faTrash = faTrash;

    constructor() {
        super();
        // Sync input to internal state
        effect(() => {
            const inputValue = this.textExercises();
            if (inputValue && inputValue.length > 0) {
                this.internalTextExercises = inputValue;
            }
        });
    }

    protected get exercises() {
        return this.internalTextExercises;
    }

    protected loadExercises(): void {
        this.courseExerciseService.findAllTextExercisesForCourse(this.courseId).subscribe({
            next: (res: HttpResponse<TextExercise[]>) => {
                this.internalTextExercises = res.body!;

                // reconnect exercise with course
                this.internalTextExercises.forEach((exercise) => {
                    exercise.course = this.course;
                    this.accountService.setAccessRightsForExercise(exercise);
                    this.selectedExercises = [];
                });
                this.applyFilter();
                this.emitExerciseCount(this.internalTextExercises.length);
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    protected applyFilter(): void {
        this.filteredTextExercises = this.internalTextExercises.filter((exercise) => this.filter.matchesExercise(exercise));
        this.emitFilteredExerciseCount(this.filteredTextExercises.length);
    }

    /**
     * Returns the unique identifier for items in the collection
     * @param _index of a text exercise in the collection
     * @param item current text exercise
     */
    trackId(_index: number, item: TextExercise) {
        return item.id;
    }

    protected getChangeEventName(): string {
        return 'textExerciseListModification';
    }

    sortRows() {
        this.sortService.sortByProperty(this.internalTextExercises, this.predicate, this.reverse);
        this.applyFilter();
    }

    /**
     * Used in the template for jhiSort
     */
    callback() {}

    openImportModal() {
        const modalRef = this.modalService.open(ExerciseImportWrapperComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exerciseType = ExerciseType.TEXT;
        modalRef.result.then((result: TextExercise) => {
            this.router.navigate(['course-management', this.courseId, 'text-exercises', result.id, 'import']);
        });
    }
}
