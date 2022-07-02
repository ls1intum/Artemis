import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faSquare, faSquareCheck } from '@fortawesome/free-regular-svg-icons';
import { faLink, faUpRightFromSquare } from '@fortawesome/free-solid-svg-icons';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';
import { LectureUnitCompletionEvent } from 'app/overview/course-lectures/course-lecture-details.component';

@Component({
    selector: 'jhi-online-unit',
    templateUrl: './online-unit.component.html',
    styleUrls: ['../lecture-unit.component.scss'],
})
export class OnlineUnitComponent {
    @Input() onlineUnit: OnlineUnit;
    @Input() isPresentationMode = false;
    @Output() onCompletion: EventEmitter<LectureUnitCompletionEvent> = new EventEmitter();

    isCollapsed = true;

    // Icons
    faLink = faLink;
    faUpRightFromSquare = faUpRightFromSquare;
    faSquare = faSquare;
    faSquareCheck = faSquareCheck;

    constructor() {}

    handleCollapse(event: Event) {
        event.stopPropagation();
        this.isCollapsed = !this.isCollapsed;
    }

    handleClick(event: Event, completed: boolean) {
        event.stopPropagation();
        this.onCompletion.emit({ lectureUnit: this.onlineUnit, completed });
    }

    openLink(event: Event) {
        event.stopPropagation();
        if (this.onlineUnit?.source) {
            window.open(this.onlineUnit.source, '_blank');
            this.onCompletion.emit({ lectureUnit: this.onlineUnit, completed: true });
        }
    }

    get domainName(): string {
        if (this.onlineUnit?.source) {
            return new URL(this.onlineUnit.source).hostname.replace('www.', '');
        }
        return '';
    }
}
