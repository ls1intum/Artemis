import { Component, output } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-close-edit-lecture-dialog',
    standalone: true,
    imports: [TranslateDirective, ArtemisSharedCommonModule],
    templateUrl: './close-edit-lecture-dialog.component.html',
    styleUrl: './close-edit-lecture-dialog.component.scss',
})
export class CloseEditLectureDialogComponent {
    protected readonly faTimes = faTimes;

    shallCloseWindow = output<boolean>();

    closeWindow(isCloseConfirmed: boolean): void {
        this.shallCloseWindow.emit(isCloseConfirmed);
    }
}
