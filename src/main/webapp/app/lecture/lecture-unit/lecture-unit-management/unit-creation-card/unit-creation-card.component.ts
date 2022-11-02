import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faCheck, faFileUpload, faLink, faScroll, faVideo } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, Router } from '@angular/router';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';

@Component({
    selector: 'jhi-unit-creation-card',
    templateUrl: './unit-creation-card.component.html',
})
export class UnitCreationCardComponent {
    @Input() emitEvents = false;

    @Output()
    onUnitCreationCardClicked: EventEmitter<LectureUnitType> = new EventEmitter<LectureUnitType>();

    unitType = LectureUnitType;

    // Icons
    faCheck = faCheck;
    faVideo = faVideo;
    faFileUpload = faFileUpload;
    faScroll = faScroll;
    faLink = faLink;

    constructor(protected activatedRoute: ActivatedRoute, private navigationUtilService: ArtemisNavigationUtilService, private router: Router) {}

    onButtonClicked(type: LectureUnitType) {
        if (this.emitEvents) {
            this.onUnitCreationCardClicked.emit(type);
            return;
        }

        let navigationTarget = [];

        switch (type) {
            case LectureUnitType.TEXT:
                navigationTarget = ['text-units', 'create'];
                break;
            case LectureUnitType.EXERCISE:
                navigationTarget = ['exercise-units', 'create'];
                break;
            case LectureUnitType.VIDEO:
                navigationTarget = ['video-units', 'create'];
                break;
            case LectureUnitType.ONLINE:
                navigationTarget = ['online-units', 'create'];
                break;
            case LectureUnitType.ATTACHMENT:
                navigationTarget = ['attachment-units', 'create'];
                break;
        }

        this.router.navigate(navigationTarget, { relativeTo: this.activatedRoute });
    }
}
