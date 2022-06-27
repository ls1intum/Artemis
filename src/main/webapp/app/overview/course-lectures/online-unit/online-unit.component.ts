import { Component, Input } from '@angular/core';
import { faLink, faUpRightFromSquare } from '@fortawesome/free-solid-svg-icons';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';

@Component({
    selector: 'jhi-online-unit',
    templateUrl: './online-unit.component.html',
    styleUrls: ['../lecture-unit.component.scss'],
})
export class OnlineUnitComponent {
    @Input() onlineUnit: OnlineUnit;

    isCollapsed = true;

    // Icons
    faLink = faLink;
    faUpRightFromSquare = faUpRightFromSquare;

    constructor() {}

    handleCollapse(event: Event) {
        event.stopPropagation();
        this.isCollapsed = !this.isCollapsed;
    }

    get domainName(): string {
        if (this.onlineUnit?.source) {
            return new URL(this.onlineUnit.source).hostname.replace('www.', '');
        }
        return '';
    }
}
