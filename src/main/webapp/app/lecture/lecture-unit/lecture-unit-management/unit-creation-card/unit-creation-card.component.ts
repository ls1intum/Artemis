import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faCheck, faFileUpload, faLink, faScroll, faVideo } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';

@Component({
    selector: 'jhi-unit-creation-card',
    templateUrl: './unit-creation-card.component.html',
})
export class UnitCreationCardComponent {
    readonly documentationType: DocumentationType = 'Units';

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

    onButtonClicked(type: LectureUnitType) {
        if (this.emitEvents) {
            this.onUnitCreationCardClicked.emit(type);
            return;
        }
    }
}
