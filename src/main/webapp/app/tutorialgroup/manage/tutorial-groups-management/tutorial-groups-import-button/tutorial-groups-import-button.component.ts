import { ChangeDetectionStrategy, Component, OnDestroy, input, output, signal, viewChild } from '@angular/core';
import { NgbDropdownButtonItem, NgbDropdownItem } from '@ng-bootstrap/ng-bootstrap';
import { Subject } from 'rxjs';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TutorialGroupsRegistrationImportDialogComponent } from 'app/tutorialgroup/manage/tutorial-groups-management/tutorial-groups-import-dialog/tutorial-groups-registration-import-dialog.component';
import { DialogModule } from 'primeng/dialog';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-tutorial-groups-import-button',
    templateUrl: './tutorial-groups-import-button.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NgbDropdownButtonItem, NgbDropdownItem, TranslateDirective, TutorialGroupsRegistrationImportDialogComponent, DialogModule, ArtemisTranslatePipe],
})
export class TutorialGroupsImportButtonComponent implements OnDestroy {
    ngUnsubscribe = new Subject<void>();

    readonly warningDialogVisible = signal<boolean>(false);
    readonly importDialog = viewChild<TutorialGroupsRegistrationImportDialogComponent>('importDialog');

    courseId = input.required<number>();

    readonly importFinished = output<void>();

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

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
