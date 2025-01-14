import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-side-panel',
    templateUrl: './side-panel.component.html',
    styleUrls: ['./side-panel.scss'],
})
export class SidePanelComponent {
    @Input() panelHeader: string;
    @Input() panelDescriptionHeader?: string;
}
