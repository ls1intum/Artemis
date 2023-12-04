import { Component, Input } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { faPlus, faSort, faTimes } from '@fortawesome/free-solid-svg-icons';

import { AlertService } from 'app/core/util/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { MathExercise } from 'app/entities/math-exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ExerciseComponent } from 'app/exercises/shared/exercise/exercise.component';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { ExerciseImportWrapperComponent } from 'app/exercises/shared/import/exercise-import-wrapper/exercise-import-wrapper.component';
import { MathExerciseService } from 'app/exercises/math/manage/math-exercise/math-exercise.service';
import { onError } from 'app/shared/util/global.utils';
import { SortService } from 'app/shared/service/sort.service';

@Component({
    selector: 'jhi-math-exercise',
    templateUrl: './math-exercise.component.html',
})
export class MathExerciseComponent extends ExerciseComponent {
    @Input() mathExercises: MathExercise[];
    filteredMathExercises: MathExercise[];

    // Icons
    faSort = faSort;
    faPlus = faPlus;
    faTimes = faTimes;

    protected get exercises() {
        return this.mathExercises;
    }

    constructor(
        public exerciseService: ExerciseService,
        public mathExerciseService: MathExerciseService,
        private courseExerciseService: CourseExerciseService,
        private modalService: NgbModal,
        private router: Router,
        courseService: CourseManagementService,
        translateService: TranslateService,
        private alertService: AlertService,
        private sortService: SortService,
        eventManager: EventManager,
        route: ActivatedRoute,
        private accountService: AccountService,
    ) {
        super(courseService, translateService, route, eventManager);
        this.mathExercises = [];
        this.filteredMathExercises = [];
    }

    protected loadExercises(): void {
        this.courseExerciseService.findAllMathExercises(this.courseId).subscribe({
            next: (res: HttpResponse<MathExercise[]>) => {
                this.mathExercises = res.body!;

                // reconnect exercise with course
                this.mathExercises.forEach((exercise) => {
                    exercise.course = this.course;
                    this.accountService.setAccessRightsForExercise(exercise);
                    this.selectedExercises = [];
                });
                this.applyFilter();
                this.emitExerciseCount(this.mathExercises.length);
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    protected applyFilter(): void {
        this.filteredMathExercises = this.mathExercises.filter((exercise) => this.filter.matchesExercise(exercise));
        this.emitFilteredExerciseCount(this.filteredMathExercises.length);
    }

    /**
     * Returns the unique identifier for items in the collection
     * @param index of a math exercise in the collection
     * @param item current math exercise
     */
    trackId(index: number, item: MathExercise) {
        return item.id;
    }

    protected getChangeEventName(): string {
        return 'mathExerciseListModification';
    }

    sortRows() {
        this.sortService.sortByProperty(this.mathExercises, this.predicate, this.reverse);
        this.applyFilter();
    }

    /**
     * Used in the template for jhiSort
     */
    callback() {}

    openImportModal() {
        const modalRef = this.modalService.open(ExerciseImportWrapperComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exerciseType = ExerciseType.MATH;
        modalRef.result.then((result: MathExercise) => {
            this.router.navigate(['course-management', this.courseId, 'math-exercises', result.id, 'import']);
        });
    }
}
