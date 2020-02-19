import { Component, Input, OnInit } from '@angular/core';

@Component({
    selector: 'jhi-score-display',
    templateUrl: './score-display.component.html',
    styleUrls: ['./score-display.component.scss'],
})
export class ScoreDisplayComponent implements OnInit {
    @Input() maxScore: number;
    @Input() score: number;
    constructor() {}

    ngOnInit() {}
}
