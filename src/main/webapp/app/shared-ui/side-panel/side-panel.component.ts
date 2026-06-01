import { Component, input } from '@angular/core';

@Component({
    selector: 'jhi-side-panel',
    templateUrl: './side-panel.component.html',
    styleUrls: ['./side-panel.scss'],
})
export class SidePanelComponent {
    panelHeader = input<string>('');
    panelDescriptionHeader = input<string>();
}
