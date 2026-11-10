import { Component, inject } from '@angular/core';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faFileImport } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExerciseManageButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-manage-button/exercise-manage-button.component';
import { FeatureToggleLinkDirective } from 'app/foundation/feature-toggle/feature-toggle-link.directive';
import { ExerciseImportDialogData } from 'app/exercise/import/exercise-import.component';
import { importFromFileNavigation } from 'app/exercise/import/import-from-file-navigation';
import { ExerciseImportTabsComponent } from 'app/exercise/import/exercise-import-tabs/exercise-import-tabs.component';
import { TumAetUiButtonComponent } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-exercise-import-button',
    imports: [TranslateDirective, FaIconComponent, FeatureToggleLinkDirective, TumAetUiButtonComponent],
    templateUrl: './exercise-import-button.component.html',
})
export class ExerciseImportButtonComponent extends ExerciseManageButtonComponent {
    private translateService = inject(TranslateService);

    protected readonly faFileImport = faFileImport;

    openImportModal() {
        this.beforeNavigate.emit();
        const exerciseType = this.exerciseType();
        const courseId = this.course()?.id;
        const dialogData: ExerciseImportDialogData = { exerciseType, courseId };

        // Determine the header key based on exercise type
        const headerKey = exerciseType === ExerciseType.FILE_UPLOAD ? 'artemisApp.fileUploadExercise.home.importLabel' : `artemisApp.${exerciseType}Exercise.home.importLabel`;

        // The tabs offer the import of an existing exercise and the import from a file
        const dialogRef = this.dialogService.open<ExerciseImportTabsComponent, Exercise, ExerciseImportDialogData>(ExerciseImportTabsComponent, {
            header: this.translateService.instant(headerKey),
            size: 'large',
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            data: dialogData,
        });

        dialogRef?.onClose.subscribe((result: Exercise | undefined) => {
            if (!result) {
                return;
            }
            if (result.id === undefined) {
                // An exercise read from a file has no id.
                const { segments, extras } = importFromFileNavigation(result);
                void this.router.navigate(['/course-management', courseId, ...segments], extras);
            } else {
                const importSegments =
                    exerciseType === ExerciseType.PROGRAMMING ? ['programming-exercises', 'import', result.id] : [`${exerciseType}-exercises`, result.id, 'import'];
                void this.router.navigate(['/course-management', courseId, ...importSegments], {
                    queryParams: { sourceIsExamExercise: !!result.exerciseGroup },
                });
            }
        });
    }
}
