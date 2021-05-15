import { Component, Input } from '@angular/core';
import * as moment from 'moment';
import { Exercise, getIconTooltip } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-not-released-tag',
    templateUrl: 'app/shared/components/not-released-tag.component.html',
})

export class NotReleasedTagComponent {
    readonly moment = moment;

    @Input() public exercise: Exercise;

    getIconTooltip = getIconTooltip;
}
