import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Component, Input } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { faPlus, faSort } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';

import { TextExerciseService } from './text-exercise.service';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { ExerciseComponent } from 'app/exercises/shared/exercise/exercise.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ExerciseImportComponent } from 'app/exercises/shared/import/exercise-import.component';
import { SortService } from 'app/shared/service/sort.service';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-text-exercise',
    templateUrl: './text-exercise.component.html',
})
export class TextExerciseComponent extends ExerciseComponent {
    @Input() textExercises: TextExercise[];
    filteredTextExercises: TextExercise[];

    // Icons
    faSort = faSort;
    faPlus = faPlus;

    constructor(
        public exerciseService: ExerciseService,
        private textExerciseService: TextExerciseService,
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
        this.textExercises = [];
        this.filteredTextExercises = [];
    }

    protected loadExercises(): void {
        this.courseExerciseService.findAllTextExercisesForCourse(this.courseId).subscribe({
            next: (res: HttpResponse<TextExercise[]>) => {
                this.textExercises = res.body!;

                // reconnect exercise with course
                this.textExercises.forEach((exercise) => {
                    exercise.course = this.course;
                    this.accountService.setAccessRightsForExercise(exercise);
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
     * @param index of a text exercise in the collection
     * @param item current text exercise
     */
    trackId(index: number, item: TextExercise) {
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
        const modalRef = this.modalService.open(ExerciseImportComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exerciseType = ExerciseType.TEXT;
        modalRef.result.then((result: TextExercise) => {
            this.router.navigate(['course-management', this.courseId, 'text-exercises', result.id, 'import']);
        });
    }
}
