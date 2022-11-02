import { Component } from '@angular/core';
import { faCheck, faFileUpload, faLink, faScroll, faVideo } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';

@Component({
    selector: 'jhi-unit-creation-card',
    templateUrl: './unit-creation-card.component.html',
})
export class UnitCreationCardComponent {
    documentationType = DocumentationType.Units;

    // Icons
    faCheck = faCheck;
    faVideo = faVideo;
    faFileUpload = faFileUpload;
    faScroll = faScroll;
    faLink = faLink;
}
