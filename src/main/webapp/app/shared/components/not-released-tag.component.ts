import { Component, Input, OnChanges, OnInit } from '@angular/core';
import * as moment from 'moment';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-not-released-tag',
    templateUrl: './not-released-tag.component.html',
})

export class NotReleasedTagComponent implements OnInit, OnChanges {
    readonly moment = moment;

    @Input() public exercise: Exercise;
    public exerciseStatusBadge = 'badge-success';

    constructor() {}

    ngOnInit(): void {
    }

    ngOnChanges(): void {
    }
}
