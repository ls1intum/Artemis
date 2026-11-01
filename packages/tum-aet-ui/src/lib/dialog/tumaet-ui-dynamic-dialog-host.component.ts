import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NgComponentOutlet } from '@angular/common';
import { TumAetUiDialogComponent } from './tumaet-ui-dialog.component';
import { TumAetUiDialogConfig, TumAetUiDialogRef } from './tumaet-ui-dialog.types';

/** Internal composition of the declarative dialog with dynamically supplied content. */
@Component({
    selector: 'tumaet-ui-dynamic-dialog-host',
    template: `
        <tumaet-ui-dialog
            [visible]="true"
            [header]="options.header"
            [ariaLabel]="options.ariaLabel"
            [size]="options.size ?? 'medium'"
            [showHeader]="options.showHeader ?? true"
            [closable]="options.closable ?? true"
            [closeOnEscape]="options.closeOnEscape ?? true"
            [dismissableMask]="options.dismissableMask ?? false"
            [autoFocus]="options.autoFocus ?? 'dialog'"
            (hidden)="ref.dismiss()"
        >
            <ng-template #header>
                @if (options.templates?.header; as header) {
                    <ng-container [ngComponentOutlet]="header" />
                } @else {
                    {{ options.header }}
                }
            </ng-template>
            <ng-content />
            @if (options.templates?.footer; as footerComponent) {
                <ng-template #footer><ng-container [ngComponentOutlet]="footerComponent" /></ng-template>
            }
        </tumaet-ui-dialog>
    `,
    imports: [TumAetUiDialogComponent, NgComponentOutlet],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiDynamicDialogHostComponent {
    protected readonly options = inject(TumAetUiDialogConfig).options;
    protected readonly ref = inject(TumAetUiDialogRef);
}
