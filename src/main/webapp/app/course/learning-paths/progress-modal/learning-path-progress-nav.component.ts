import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faArrowsRotate, faArrowsToEye, faXmark } from '@fortawesome/free-solid-svg-icons';
import { LearningPathPageableSearchDTO } from 'app/entities/competency/learning-path.model';

@Component({
    selector: 'jhi-learning-path-progress-nav',
    templateUrl: './learning-path-progress-nav.component.html',
})
export class LearningPathProgressNavComponent {
    @Input() learningPath: LearningPathPageableSearchDTO;
    @Output() onRefresh: EventEmitter<void> = new EventEmitter();
    @Output() onCenterView: EventEmitter<void> = new EventEmitter();
    @Output() onClose: EventEmitter<void> = new EventEmitter();

    faXmark = faXmark;
    faArrowsToEye = faArrowsToEye;
    faArrowsRotate = faArrowsRotate;
}
