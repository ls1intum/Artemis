import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
    selector: 'jhi-split-pane-header',
    templateUrl: './split-pane-header.component.html',
    styleUrls: ['./split-pane-header.component.scss'],
})
export class SplitPaneHeaderComponent {
    @Input() files: string[];
    @Input() studentLogin: string;
    @Output() selectFile = new EventEmitter<string>();

    public showFiles = false;

    toggleShowFiles() {
        this.showFiles = !this.showFiles;
    }
}
