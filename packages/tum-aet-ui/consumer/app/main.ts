import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { bootstrapApplication } from '@angular/platform-browser';
import {
    TumAetUiButtonComponent,
    TumAetUiButtonDirective,
    TumAetUiCheckboxComponent,
    TumAetUiDialogComponent,
    TumAetUiMenuComponent,
    TumAetUiMenuItemDirective,
    TumAetUiMenuTriggerDirective,
    TumAetUiSelectComponent,
    TumAetUiTabComponent,
    TumAetUiTabListComponent,
    TumAetUiTabPanelComponent,
    TumAetUiTabPanelsComponent,
    TumAetUiTabsComponent,
} from '@tumaet/ui-angular';

@Component({
    selector: 'consumer-app',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        ReactiveFormsModule,
        TumAetUiButtonComponent,
        TumAetUiButtonDirective,
        TumAetUiCheckboxComponent,
        TumAetUiDialogComponent,
        TumAetUiMenuComponent,
        TumAetUiMenuItemDirective,
        TumAetUiMenuTriggerDirective,
        TumAetUiSelectComponent,
        TumAetUiTabComponent,
        TumAetUiTabListComponent,
        TumAetUiTabPanelComponent,
        TumAetUiTabPanelsComponent,
        TumAetUiTabsComponent,
    ],
    template: `
        <h1>Package consumer</h1>
        <label><tumaet-ui-checkbox [formControl]="accepted" /> Accept terms</label>
        <tumaet-ui-button [disabled]="!accepted.value" (clicked)="visible.set(true)">Open dialog</tumaet-ui-button>
        <tumaet-ui-select [options]="['Student', 'Instructor']" [filter]="true" ariaLabel="Role" placeholder="Choose a role" />
        <tumaet-ui-dialog header="Published package" [(visible)]="visible">
            <p>Rendered from the npm tarball.</p>
        </tumaet-ui-dialog>
        <button tumAetUiButton [tumAetUiMenuTrigger]="actions">Course actions</button>
        <ng-template #actions>
            <tumaet-ui-menu>
                <button tumAetUiMenuItem (triggered)="chosen.set('students')">Add students</button>
                <button tumAetUiMenuItem (triggered)="chosen.set('tutors')">Add tutors</button>
            </tumaet-ui-menu>
        </ng-template>
        <output>Chosen: {{ chosen() }}</output>
        <tumaet-ui-tabs [(value)]="tab">
            <tumaet-ui-tab-list aria-label="Sections">
                <tumaet-ui-tab [value]="1">Overview</tumaet-ui-tab>
                <tumaet-ui-tab [value]="2">Settings</tumaet-ui-tab>
            </tumaet-ui-tab-list>
            <tumaet-ui-tab-panels>
                <tumaet-ui-tab-panel [value]="1">Overview panel</tumaet-ui-tab-panel>
                <tumaet-ui-tab-panel [value]="2">Settings panel</tumaet-ui-tab-panel>
            </tumaet-ui-tab-panels>
        </tumaet-ui-tabs>
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
