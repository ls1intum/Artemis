import { Component, Input, OnInit, inject } from '@angular/core';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { NgbNav, NgbNavContent, NgbNavItem, NgbNavItemRole, NgbNavLink, NgbNavLinkBase, NgbNavOutlet } from '@ng-bootstrap/ng-bootstrap';
import { DynamicDialogConfig } from 'primeng/dynamicdialog';
import { TranslateDirective } from 'app/shared/language/translate.directive';
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
export class ExerciseImportTabsComponent implements OnInit {
    private dialogConfig = inject(DynamicDialogConfig, { optional: true });

    activeTab = 1;
    @Input() exerciseType: ExerciseType;

    ngOnInit(): void {
        // Get data from DynamicDialogConfig if available (when opened via DialogService)
        const dialogData = this.dialogConfig?.data as ExerciseImportDialogData | undefined;
        if (dialogData?.exerciseType) {
            this.exerciseType = dialogData.exerciseType;
        }
    }
}
