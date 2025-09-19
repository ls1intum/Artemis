import { Component, input } from '@angular/core';

@Component({
    selector: 'jhi-info-panel',
    templateUrl: './info-panel.component.html',
    styleUrls: ['./info-panel.scss'],
})
export class InfoPanelComponent {
    readonly panelHeader = input.required<string>();
    readonly panelDescriptionHeader = input<string>();
}
