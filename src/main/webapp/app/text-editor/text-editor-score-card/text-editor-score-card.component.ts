import { Component, Input, OnInit } from '@angular/core';
import { Feedback } from 'app/entities/feedback';
import { HighlightColors } from 'app/text-shared/highlight-colors';

@Component({
    selector: 'jhi-text-editor-score-card',
    templateUrl: './text-editor-score-card.component.html',
    styleUrls: ['./text-editor-score-card.component.scss']
})
export class TextEditorScoreCardComponent implements OnInit {
    @Input() feedback: Feedback;
    @Input() public highlightColor: HighlightColors.Color;

    constructor() {}

    ngOnInit() {}
}
