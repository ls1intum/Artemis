import { Component, Input, inject } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ExerciseType } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text/text-exercise.model';
import { TextExerciseService } from './text-exercise.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Router, RouterLink } from '@angular/router';
import { ExerciseComponent } from 'app/exercises/shared/exercise/exercise.component';
import { onError } from 'app/shared/util/global.utils';
import { AccountService } from 'app/core/auth/account.service';
import { SortService } from 'app/shared/service/sort.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AlertService } from 'app/core/util/alert.service';
import { faPlus, faSort, faTrash } from '@fortawesome/free-solid-svg-icons';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { ExerciseImportWrapperComponent } from 'app/exercises/shared/import/exercise-import-wrapper/exercise-import-wrapper.component';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { FormsModule } from '@angular/forms';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExerciseCategoriesComponent } from 'app/shared/exercise-categories/exercise-categories.component';
import { TextExerciseRowButtonsComponent } from './text-exercise-row-buttons.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

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

    @Input() textExercises: TextExercise[] = [];
    filteredTextExercises: TextExercise[] = [];

    // Icons
    faSort = faSort;
    faPlus = faPlus;
    faTrash = faTrash;

    protected get exercises() {
        return this.textExercises;
    }

    protected loadExercises(): void {
        this.courseExerciseService.findAllTextExercisesForCourse(this.courseId).subscribe({
            next: (res: HttpResponse<TextExercise[]>) => {
                this.textExercises = res.body!;

                // reconnect exercise with course
                this.textExercises.forEach((exercise) => {
                    exercise.course = this.course;
                    this.accountService.setAccessRightsForExercise(exercise);
                    this.selectedExercises = [];
                });
                this.applyFilter();
                this.emitExerciseCount(this.textExercises.length);
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    protected applyFilter(): void {
        this.filteredTextExercises = this.textExercises.filter((exercise) => this.filter.matchesExercise(exercise));
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
        this.sortService.sortByProperty(this.textExercises, this.predicate, this.reverse);
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
