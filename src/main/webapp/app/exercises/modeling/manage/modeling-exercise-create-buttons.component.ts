import { Component, Input } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { ExerciseImportWrapperComponent } from 'app/exercises/shared/import/exercise-import-wrapper/exercise-import-wrapper.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Router } from '@angular/router';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';

@Component({
    selector: 'jhi-modeling-exercise-create-buttons',
    templateUrl: './modeling-exercise-create-buttons.component.html',
})
export class ModelingExerciseCreateButtonsComponent {
    @Input() course: Course;

    faPlus = faPlus;

    constructor(private router: Router, private modalService: NgbModal) {}

    openImportModal() {
        const modalRef = this.modalService.open(ExerciseImportWrapperComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exerciseType = ExerciseType.MODELING;
        modalRef.result.then((result: ModelingExercise) => {
            this.router.navigate(['course-management', this.course.id, 'modeling-exercises', result.id, 'import']);
        });
    }
}
