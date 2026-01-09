import { Component, Type, inject } from '@angular/core';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faFileImport } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExerciseManageButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-manage-button/exercise-manage-button.component';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { ExerciseImportComponent, ExerciseImportDialogData } from 'app/exercise/import/exercise-import.component';
import { ExerciseImportTabsComponent } from 'app/exercise/import/exercise-import-tabs/exercise-import-tabs.component';

@Component({
    selector: 'jhi-exercise-import-button',
    imports: [TranslateDirective, FaIconComponent, FeatureToggleLinkDirective],
    templateUrl: './exercise-import-button.component.html',
})
export class ExerciseImportButtonComponent extends ExerciseManageButtonComponent {
    private translateService = inject(TranslateService);

    protected readonly faFileImport = faFileImport;

    openImportModal() {
        const exerciseType = this.exerciseType();
        const dialogData: ExerciseImportDialogData = { exerciseType };

        // Determine the header key based on exercise type
        const headerKey = exerciseType === ExerciseType.FILE_UPLOAD ? 'artemisApp.fileUploadExercise.home.importLabel' : `artemisApp.${exerciseType}Exercise.home.importLabel`;

        // For programming exercises, use tabs component (allows import from file), otherwise use direct import
        const componentToOpen: Type<any> = exerciseType === ExerciseType.PROGRAMMING ? ExerciseImportTabsComponent : ExerciseImportComponent;

        const dialogRef = this.dialogService.open(componentToOpen, {
            header: this.translateService.instant(headerKey),
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            draggable: false,
            data: dialogData,
        });

        dialogRef?.onClose.subscribe((result: Exercise | undefined) => {
            if (result) {
                if (this.exerciseType() === ExerciseType.PROGRAMMING) {
                    this.handleProgrammingImport(result);
                } else {
                    this.router.navigate(['/course-management', this.course()?.id, this.exerciseType() + '-exercises', result.id, 'import']);
                }
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
