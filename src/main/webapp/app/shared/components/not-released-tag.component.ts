import { Component, Input } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-not-released-tag',
    templateUrl: './not-released-tag.component.html',
})
export class NotReleasedTagComponent {
    @Input() public exercise: Exercise;
    readonly dayjs = dayjs;
}
