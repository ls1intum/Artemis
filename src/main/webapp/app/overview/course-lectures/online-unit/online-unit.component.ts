import { Component, Input, OnInit } from '@angular/core';
import { faVideo } from '@fortawesome/free-solid-svg-icons';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';

@Component({
    selector: 'jhi-online-unit',
    templateUrl: './online-unit.component.html',
    styleUrls: ['../lecture-unit.component.scss'],
})
export class OnlineUnitComponent implements OnInit {
    @Input() onlineUnit: OnlineUnit;

    onlineUrl: string;
    isCollapsed = true;

    // Icons
    faVideo = faVideo;

    constructor() {}

    ngOnInit() {
        if (this.onlineUnit?.source) {
            // Validate the URL before displaying it
            // if (this.onlineUrlAllowList.some((r) => r.test(this.onlineUnit.source!)) || !urlParser || urlParser.parse(this.onlineUnit.source)) {
            this.onlineUrl = this.onlineUnit.source;
            // }
        }
    }

    handleCollapse(event: Event) {
        event.stopPropagation();
        this.isCollapsed = !this.isCollapsed;
    }
}
