import { ChangeDetectionStrategy, Component, input, output, signal, viewChild } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFileImport } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonDirective, TumUiTooltipDirective } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TutorialGroupsRegistrationImportDialogComponent } from 'app/tutorialgroup/manage/tutorial-groups-management/tutorial-groups-import-dialog/tutorial-groups-registration-import-dialog.component';
import { DialogModule } from 'primeng/dialog';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-tutorial-groups-import-button',
    templateUrl: './tutorial-groups-import-button.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        TranslateDirective,
        TutorialGroupsRegistrationImportDialogComponent,
        DialogModule,
        FaIconComponent,
        TumUiButtonDirective,
        TumUiTooltipDirective,
        ArtemisTranslatePipe,
    ],
})
export class TutorialGroupsImportButtonComponent {
    readonly warningDialogVisible = signal<boolean>(false);
    readonly importDialog = viewChild<TutorialGroupsRegistrationImportDialogComponent>('importDialog');

    courseId = input.required<number>();

    readonly importFinished = output<void>();

    protected readonly faFileImport = faFileImport;

    openTutorialGroupImportDialog(event: MouseEvent) {
        event.stopPropagation();
        this.importDialog()?.open();
    }

    onImportCompleted(): void {
        this.warningDialogVisible.set(true);
    }

    closeWarningDialog() {
        this.warningDialogVisible.set(false);
        this.importFinished.emit();
    }
}
