import { Component, OnInit, Input } from '@angular/core';

@Component({
    selector: 'jhi-side-panel',
    templateUrl: './side-panel.component.html',
    styleUrls: ['./side-panel.scss']
})
export class SidePanelComponent implements OnInit {

    @Input() panelHeader: string;

    constructor() {
    }

    ngOnInit() {
    }

}
