import { Component, Input, Output, EventEmitter } from '@angular/core';
import { faCheck, faFileUpload, faLink, faScroll, faVideo } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, Router } from '@angular/router';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';

export enum UnitType {
    TEXT,
    EXERCISE,
    VIDEO,
    ATTACHMENT,
    ONLINE,
}

@Component({
    selector: 'jhi-unit-creation-card',
    templateUrl: './unit-creation-card.component.html',
})
export class UnitCreationCardComponent {
    @Input() emitEvents = false;

    @Output()
    onUnitCreationCardClicked: EventEmitter<UnitType> = new EventEmitter<UnitType>();

    unitType = UnitType;

    // Icons
    faCheck = faCheck;
    faVideo = faVideo;
    faFileUpload = faFileUpload;
    faScroll = faScroll;
    faLink = faLink;

    constructor(protected activatedRoute: ActivatedRoute, private navigationUtilService: ArtemisNavigationUtilService, private router: Router) {}

    onButtonClicked(type: UnitType) {
        if (this.emitEvents) {
            this.onUnitCreationCardClicked.emit(type);
            return;
        }

        let navigationTarget = [];

        switch (type) {
            case UnitType.TEXT:
                navigationTarget = ['text-units', 'create'];
                break;
            case UnitType.EXERCISE:
                navigationTarget = ['exercise-units', 'create'];
                break;
            case UnitType.VIDEO:
                navigationTarget = ['video-units', 'create'];
                break;
            case UnitType.ONLINE:
                navigationTarget = ['online-units', 'create'];
                break;
            case UnitType.ATTACHMENT:
                navigationTarget = ['attachment-units', 'create'];
                break;
        }

        this.router.navigate(navigationTarget, { relativeTo: this.activatedRoute });
    }
}
