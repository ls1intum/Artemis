import { Component, computed, inject, input, output } from '@angular/core';

import { ButtonSize } from 'app/shared-ui/components/buttons/button/button.component';
import { CsvExportOptions, ExportDialogCloseResult, ExportModalComponent, isExportDialogCancelledResult } from 'app/shared-ui/export/modal/export-modal.component';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumAetUiButtonDirective, TumAetUiButtonSize } from '@tumaet/ui-angular/button';
import { TumAetUiDialogService } from '@tumaet/ui-angular/dialog';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-csv-export-button',
    template: ` <button
        type="button"
        tumAetUiButton
        severity="secondary"
        variant="outlined"
        [size]="tumAetUiSize()"
        [disabled]="disabled()"
        [title]="title() ? (title() | artemisTranslate) : ''"
        (click)="openExportModal($event)"
    >
        @if (icon()) {
            <fa-icon [icon]="icon()!" />
        }
        @if (title()) {
            <span [class.title-bar-collapsible-label]="collapsibleLabel()" [jhiTranslate]="title()"></span>
        }
    </button>`,
    imports: [TumAetUiButtonDirective, FontAwesomeModule, TranslateDirective, ArtemisTranslatePipe],
})
export class ExportButtonComponent {
    private dialogService = inject(TumAetUiDialogService);
    private translateService = inject(TranslateService);

    title = input<string>('');
    /**
     * A label only collapses inside a title bar, whose container query never matches anywhere else. Usages that render
     * the button into a bar opt in; the ones that put it in the page body keep their label at full length.
     */
    collapsibleLabel = input<boolean>(false);
    disabled = input<boolean>(false);
    buttonSize = input<ButtonSize>(ButtonSize.MEDIUM);
    icon = input<IconProp>();

    onExport = output<CsvExportOptions | undefined>();

    tumAetUiSize = computed<TumAetUiButtonSize>(() => {
        switch (this.buttonSize()) {
            case ButtonSize.SMALL:
                return 'small';
            case ButtonSize.LARGE:
                return 'large';
            default:
                return 'default';
        }
    });

    /**
     * Open up export option modal
     * @param {Event} event - Mouse Event which invoked the opening
     */
    openExportModal(event: MouseEvent) {
        event.stopPropagation();
        // Closing without a choice would read as an Excel export, so the dialog is only left through its own buttons.
        const dialogRef = this.dialogService.open<ExportModalComponent, ExportDialogCloseResult>(ExportModalComponent, {
            size: 'medium',
            showHeader: false,
            ariaLabel: this.translateService.instant('export.dialogTitle'),
            closable: false,
            closeOnEscape: false,
            dismissableMask: false,
        });
        dialogRef.onClose.subscribe((result) => {
            if (!isExportDialogCancelledResult(result)) {
                this.onExport.emit(result);
            }
        });
    }
}
