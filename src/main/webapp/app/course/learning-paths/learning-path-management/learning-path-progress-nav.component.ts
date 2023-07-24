import { Component, EventEmitter, Output } from '@angular/core';
import { faArrowsRotate, faArrowsToEye, faXmark } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-learning-path-progress-nav',
    templateUrl: './learning-path-progress-nav.component.html',
})
export class LearningPathProgressNavComponent {
    @Output() onRefresh: EventEmitter<void> = new EventEmitter();
    @Output() onCenterView: EventEmitter<void> = new EventEmitter();
    @Output() onClose: EventEmitter<void> = new EventEmitter();

    // icons
    faXmark = faXmark;
    faArrowsToEye = faArrowsToEye;
    faArrowsRotate = faArrowsRotate;
}
