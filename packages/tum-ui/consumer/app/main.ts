import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { bootstrapApplication } from '@angular/platform-browser';
import { TumUiButtonComponent, TumUiCheckboxComponent, TumUiDialogComponent, TumUiSelectComponent } from '@tumaet/ui-angular';

@Component({
    selector: 'consumer-app',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ReactiveFormsModule, TumUiButtonComponent, TumUiCheckboxComponent, TumUiDialogComponent, TumUiSelectComponent],
    template: `
        <h1>Package consumer</h1>
        <label><tum-ui-checkbox [formControl]="accepted" /> Accept terms</label>
        <tum-ui-button [disabled]="!accepted.value" (clicked)="visible.set(true)">Open dialog</tum-ui-button>
        <tum-ui-select [options]="['Student', 'Instructor']" [filter]="true" ariaLabel="Role" placeholder="Choose a role" />
        <tum-ui-dialog header="Published package" [(visible)]="visible">
            <p>Rendered from the npm tarball.</p>
        </tum-ui-dialog>
    `,
    styles: `
        :host {
            display: grid;
            justify-items: start;
            gap: 1rem;
            padding: 1.5rem;
        }
        h1,
        label {
            font-family: var(--tumaet-ui-font-family);
        }
        h1 {
            margin: 0;
            font-size: 1.5rem;
        }
        label {
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }
    `,
})
class ConsumerApp {
    readonly accepted = new FormControl(false, { nonNullable: true });
    readonly visible = signal(false);
}

void bootstrapApplication(ConsumerApp);
