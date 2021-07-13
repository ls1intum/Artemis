import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-info-panel',
    templateUrl: './info-panel.component.html',
    styleUrls: ['./info-panel.scss'],
})
export class InfoPanelComponent implements OnInit {
    @Input() panelHeader: string;
    @Input() panelDescriptionHeader: string;

    constructor() {}

    /**
     * Do nothing on initialization.
     */
    ngOnInit() {}
}
