import { Component } from '@angular/core';
import { ExerciseImportWrapperComponent } from 'app/exercise/import/exercise-import-wrapper/exercise-import-wrapper.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faFileImport } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExerciseManageButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-manage-button/exercise-manage-button.component';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';

@Component({
    selector: 'jhi-exercise-import-button',
    imports: [TranslateDirective, FaIconComponent, FeatureToggleLinkDirective],
    templateUrl: './exercise-import-button.component.html',
})
export class ExerciseImportButtonComponent extends ExerciseManageButtonComponent {
    protected readonly faFileImport = faFileImport;

    openImportModal() {
        const modalRef = this.modalService.open(ExerciseImportWrapperComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exerciseType = this.exerciseType();
        modalRef.result.then((result: Exercise) => {
            this.modalService.dismissAll();
            if (this.exerciseType() === ExerciseType.PROGRAMMING) {
                this.handleProgrammingImport(result);
            } else {
                this.router.navigate(['/course-management', this.course()?.id, this.exerciseType() + '-exercises', result.id, 'import']);
            }
        });
    }

    handleProgrammingImport(result: Exercise) {
        //when the file is uploaded we set the id to undefined.
        if (result.id === undefined) {
            this.router.navigate(['/course-management', this.course()?.id, 'programming-exercises', 'import-from-file'], {
                state: {
                    programmingExerciseForImportFromFile: result,
                },
            });
        } else {
            this.router.navigate(['/course-management', this.course()?.id, 'programming-exercises', 'import', result.id]);
        }
    }

    protected getTranslationSuffix(): string {
        return 'importLabel';
    }
}
