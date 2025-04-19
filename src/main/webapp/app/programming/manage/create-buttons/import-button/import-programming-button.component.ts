import { Component, inject, input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ExerciseImportWrapperComponent } from 'app/exercise/import/exercise-import-wrapper/exercise-import-wrapper.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { faFileImport, faKeyboard } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Router } from '@angular/router';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';

@Component({
    selector: 'jhi-programming-import-button',
    imports: [TranslateDirective, FaIconComponent, FeatureToggleDirective],
    templateUrl: './import-programming-button.component.html',
})
export class ImportProgrammingButtonComponent {
    course = input<Course | undefined>();
    translationKey = input<string>('artemisApp.programmingExercise.home.importLabel');
    private modalService = inject(NgbModal);
    private router = inject(Router);
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly faFileImport = faFileImport;
    protected readonly faKeyboard = faKeyboard;

    openImportModal() {
        const modalRef = this.modalService.open(ExerciseImportWrapperComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exerciseType = ExerciseType.PROGRAMMING;
        modalRef.result.then((result: ProgrammingExercise) => {
            this.modalService.dismissAll();
            //when the file is uploaded we set the id to undefined.
            if (result.id === undefined) {
                this.router.navigate(['course-management', this.course()?.id, 'programming-exercises', 'import-from-file'], {
                    state: {
                        programmingExerciseForImportFromFile: result,
                    },
                });
            } else {
                this.router.navigate(['course-management', this.course()?.id, 'programming-exercises', 'import', result.id]);
            }
        });
    }
}
