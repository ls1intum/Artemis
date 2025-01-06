import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-info-panel',
    templateUrl: './info-panel.component.html',
    styleUrls: ['./info-panel.scss'],
    standalone: false,
})
export class InfoPanelComponent {
    @Input() panelHeader: string;
    @Input() panelDescriptionHeader: string;
}
