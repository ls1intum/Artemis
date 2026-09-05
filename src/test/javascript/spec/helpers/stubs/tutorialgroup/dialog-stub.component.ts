import { Component, TemplateRef, contentChild, input, model } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';

/**
 * Stands in for the TUM UI dialog. It renders both slots the real one has - the projected body and the
 * `#footer` template - so a spec can still reach the footer buttons, and it two-way binds `visible` so a
 * spec can assert that the host closed the dialog.
 */
@Component({
    selector: 'tum-ui-dialog',
    imports: [NgTemplateOutlet],
    template: `<ng-content /><ng-container [ngTemplateOutlet]="footerTemplate() ?? null" />`,
})
export class DialogStubComponent {
    readonly header = input<string>('');
    readonly size = input<string>();
    readonly visible = model(false);
    protected readonly footerTemplate = contentChild('footer', { read: TemplateRef });
}
