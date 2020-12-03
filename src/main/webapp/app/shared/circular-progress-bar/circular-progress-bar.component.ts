import { Component, Input, OnInit } from '@angular/core';

@Component({
    selector: 'jhi-circular-progress-bar',
    templateUrl: './circular-progress-bar.component.html',
    styleUrls: ['./circular-progress-bar.component.scss'],
})
export class CircularProgressBarComponent implements OnInit {
    @Input()
    progressInPercent = 0;
    @Input()
    progressText = 'Completed';

    constructor() {}

    ngOnInit(): void {}
}
