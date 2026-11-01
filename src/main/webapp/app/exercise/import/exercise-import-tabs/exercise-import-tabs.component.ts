import { Component, computed, inject, input } from '@angular/core';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TumAetUiTabComponent, TumAetUiTabListComponent, TumAetUiTabPanelComponent, TumAetUiTabPanelsComponent, TumAetUiTabsComponent } from '@tumaet/ui-angular';
import { TumAetUiDialogConfig } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExerciseImportComponent, ExerciseImportDialogData } from '../exercise-import.component';
import { ExerciseImportFromFileComponent } from '../from-file/exercise-import-from-file.component';

@Component({
    selector: 'jhi-exercise-import-tabs',
    templateUrl: './exercise-import-tabs.component.html',
    imports: [
        TumAetUiTabsComponent,
        TumAetUiTabListComponent,
        TumAetUiTabComponent,
        TumAetUiTabPanelsComponent,
        TumAetUiTabPanelComponent,
        TranslateDirective,
        ExerciseImportComponent,
        ExerciseImportFromFileComponent,
    ],
})
export class ExerciseImportTabsComponent {
    private dialogConfig = inject(TumAetUiDialogConfig, { optional: true });

    activeTab = 1;
    exerciseType = input<ExerciseType | undefined>();
    protected readonly selectedExerciseType = computed(() => (this.dialogConfig?.data as ExerciseImportDialogData | undefined)?.exerciseType ?? this.exerciseType());
}
