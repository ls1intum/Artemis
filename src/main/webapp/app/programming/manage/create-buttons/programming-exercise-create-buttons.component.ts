import { Component, Input, inject } from '@angular/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faFileImport, faKeyboard, faPlus } from '@fortawesome/free-solid-svg-icons';
import { ExerciseImportWrapperComponent } from 'app/exercise/import/exercise-import-wrapper/exercise-import-wrapper.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Router, RouterLink } from '@angular/router';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';

@Component({
    selector: 'jhi-programming-exercise-create-buttons',
    templateUrl: './programming-exercise-create-buttons.component.html',
    imports: [FeatureToggleLinkDirective, RouterLink, FaIconComponent, TranslateDirective, FeatureToggleDirective],
})
export class ProgrammingExerciseCreateButtonsComponent {
    private router = inject(Router);
    private modalService = inject(NgbModal);

    readonly FeatureToggle = FeatureToggle;

    @Input()
    course: Course;

    faPlus = faPlus;
    faFileImport = faFileImport;
    faKeyboard = faKeyboard;

    openImportModal() {
        const modalRef = this.modalService.open(ExerciseImportWrapperComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exerciseType = ExerciseType.PROGRAMMING;
        modalRef.result.then((result: ProgrammingExercise) => {
            //when the file is uploaded we set the id to undefined.
            if (result.id === undefined) {
                this.router.navigate(['course-management', this.course.id, 'programming-exercises', 'import-from-file'], {
                    state: {
                        programmingExerciseForImportFromFile: result,
                    },
                });
            } else {
                this.router.navigate(['course-management', this.course.id, 'programming-exercises', 'import', result.id]);
            }
        });
    }
}
