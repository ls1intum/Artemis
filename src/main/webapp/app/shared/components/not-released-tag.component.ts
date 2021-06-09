import { Component, Input } from '@angular/core';
import * as moment from 'moment';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-not-released-tag',
    templateUrl: './not-released-tag.component.html',
})
export class NotReleasedTagComponent {
    readonly moment = moment;

    @Input() public exercise: Exercise;
    public exerciseStatusBadge = 'bg-success';
}
