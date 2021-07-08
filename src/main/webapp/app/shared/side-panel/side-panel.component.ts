import { Component, Input, OnInit } from '@angular/core';

@Component({
    selector: 'jhi-side-panel',
    templateUrl: './side-panel.component.html',
    styleUrls: ['./side-panel.scss'],
})
export class SidePanelComponent implements OnInit {
    @Input() panelHeader: string;
    @Input() panelDescriptionHeader?: string;

    constructor() {}

    /**
     * Do nothing on initialization.
     */
    ngOnInit() {}
}
