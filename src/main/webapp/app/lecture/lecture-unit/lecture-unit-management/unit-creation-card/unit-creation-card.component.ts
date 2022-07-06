import { Component } from '@angular/core';
import { faCheck, faFileUpload, faLink, faScroll, faVideo } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-unit-creation-card',
    templateUrl: './unit-creation-card.component.html',
    styleUrls: ['./unit-creation-card.component.scss'],
})
export class UnitCreationCardComponent {
    // Icons
    faCheck = faCheck;
    faVideo = faVideo;
    faFileUpload = faFileUpload;
    faScroll = faScroll;
    faLink = faLink;
}
