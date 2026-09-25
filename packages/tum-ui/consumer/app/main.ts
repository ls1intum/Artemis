import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { bootstrapApplication } from '@angular/platform-browser';
import {
    TumUiButtonComponent,
    TumUiButtonDirective,
    TumUiCheckboxComponent,
    TumUiDialogComponent,
    TumUiMenuComponent,
    TumUiMenuItemDirective,
    TumUiMenuTriggerDirective,
    TumUiSelectComponent,
    TumUiTabComponent,
    TumUiTabListComponent,
    TumUiTabPanelComponent,
    TumUiTabPanelsComponent,
    TumUiTabsComponent,
} from '@tumaet/ui-angular';

@Component({
    selector: 'consumer-app',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        ReactiveFormsModule,
        TumUiButtonComponent,
        TumUiButtonDirective,
        TumUiCheckboxComponent,
        TumUiDialogComponent,
        TumUiMenuComponent,
        TumUiMenuItemDirective,
        TumUiMenuTriggerDirective,
        TumUiSelectComponent,
        TumUiTabComponent,
        TumUiTabListComponent,
        TumUiTabPanelComponent,
        TumUiTabPanelsComponent,
        TumUiTabsComponent,
    ],
    template: `
        <h1>Package consumer</h1>
        <label><tum-ui-checkbox [formControl]="accepted" /> Accept terms</label>
        <tum-ui-button [disabled]="!accepted.value" (clicked)="visible.set(true)">Open dialog</tum-ui-button>
        <tum-ui-select [options]="['Student', 'Instructor']" [filter]="true" ariaLabel="Role" placeholder="Choose a role" />
        <tum-ui-dialog header="Published package" [(visible)]="visible">
            <p>Rendered from the npm tarball.</p>
        </tum-ui-dialog>
        <button tumUiButton [tumUiMenuTrigger]="actions">Course actions</button>
        <ng-template #actions>
            <tum-ui-menu>
                <button tumUiMenuItem (triggered)="chosen.set('students')">Add students</button>
                <button tumUiMenuItem (triggered)="chosen.set('tutors')">Add tutors</button>
            </tum-ui-menu>
        </ng-template>
        <output>Chosen: {{ chosen() }}</output>
        <tum-ui-tabs [(value)]="tab">
            <tum-ui-tab-list aria-label="Sections">
                <tum-ui-tab [value]="1">Overview</tum-ui-tab>
                <tum-ui-tab [value]="2">Settings</tum-ui-tab>
            </tum-ui-tab-list>
            <tum-ui-tab-panels>
                <tum-ui-tab-panel [value]="1">Overview panel</tum-ui-tab-panel>
                <tum-ui-tab-panel [value]="2">Settings panel</tum-ui-tab-panel>
            </tum-ui-tab-panels>
        </tum-ui-tabs>
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
    readonly chosen = signal('nothing');
    readonly tab = signal<number | string | undefined>(1);
}

void bootstrapApplication(ConsumerApp);
