import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NgClass } from '@angular/common';
import { CourseLearnerProfileLevel } from 'app/core/learner-profile/shared/entities/learner-profile-options.model';

@Component({
    selector: 'jhi-segmented-toggle',
    standalone: true,
    imports: [NgClass],
    templateUrl: './segmented-toggle.component.html',
})
export class SegmentedToggleComponent {
    @Input() options: { label: string; value: CourseLearnerProfileLevel }[] = [];
    @Input() selected: CourseLearnerProfileLevel;
    @Output() selectedChange = new EventEmitter<CourseLearnerProfileLevel>();

    select(value: CourseLearnerProfileLevel) {
        this.selected = value;
        this.selectedChange.emit(value);
    }
}
