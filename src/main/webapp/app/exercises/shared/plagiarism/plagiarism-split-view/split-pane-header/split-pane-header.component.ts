import { Component, Input, OnInit } from '@angular/core';

@Component({
    selector: 'jhi-split-pane-header',
    templateUrl: './split-pane-header.component.html',
    styleUrls: ['./split-pane-header.component.scss'],
})
export class SplitPaneHeaderComponent implements OnInit {
    @Input() studentLogin: string;

    @Input() files: string[];

    showFiles = false;

    constructor() {}

    ngOnInit(): void {}

    toggleShowFiles() {
        this.showFiles = !this.showFiles;
    }
}
