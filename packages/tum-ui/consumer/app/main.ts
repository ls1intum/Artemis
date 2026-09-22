import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { bootstrapApplication } from '@angular/platform-browser';
import { TumUiButtonComponent, TumUiCheckboxComponent, TumUiDialogComponent } from '@tumaet/ui-angular';

@Component({
    selector: 'consumer-app',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ReactiveFormsModule, TumUiButtonComponent, TumUiCheckboxComponent, TumUiDialogComponent],
    template: `
        <h1>Package consumer</h1>
        <tum-ui-checkbox [formControl]="accepted" ariaLabel="Accept terms" />
        <tum-ui-button [disabled]="!accepted.value" (clicked)="visible.set(true)">Open dialog</tum-ui-button>
        <tum-ui-dialog header="Published package" [(visible)]="visible">
            <p>Rendered from the npm tarball.</p>
        </tum-ui-dialog>
    `,
})
class ConsumerApp {
    readonly accepted = new FormControl(false, { nonNullable: true });
    readonly visible = signal(false);
}

void bootstrapApplication(ConsumerApp);
