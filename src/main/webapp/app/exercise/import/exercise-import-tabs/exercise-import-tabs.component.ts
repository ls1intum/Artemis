import { Component, computed, inject, input } from '@angular/core';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { NgbNav, NgbNavContent, NgbNavItem, NgbNavItemRole, NgbNavLink, NgbNavLinkBase, NgbNavOutlet } from '@ng-bootstrap/ng-bootstrap';
import { DynamicDialogConfig } from 'primeng/dynamicdialog';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExerciseImportComponent, ExerciseImportDialogData } from '../exercise-import.component';
import { ExerciseImportFromFileComponent } from '../from-file/exercise-import-from-file.component';

@Component({
    selector: 'jhi-exercise-import-tabs',
    templateUrl: './exercise-import-tabs.component.html',
    imports: [
        NgbNav,
        NgbNavItem,
        NgbNavItemRole,
        NgbNavLink,
        NgbNavLinkBase,
        TranslateDirective,
        NgbNavContent,
        ExerciseImportComponent,
        ExerciseImportFromFileComponent,
        NgbNavOutlet,
    ],
})
export class ExerciseImportTabsComponent {
    private dialogConfig = inject(DynamicDialogConfig, { optional: true });

    activeTab = 1;
    exerciseType = input<ExerciseType | undefined>();
    protected readonly selectedExerciseType = computed(() => (this.dialogConfig?.data as ExerciseImportDialogData | undefined)?.exerciseType ?? this.exerciseType());
}
