import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IDragAndDropSubmittedAnswer } from 'app/shared/model/drag-and-drop-submitted-answer.model';

@Component({
    selector: 'jhi-drag-and-drop-submitted-answer-detail',
    templateUrl: './drag-and-drop-submitted-answer-detail.component.html'
})
export class DragAndDropSubmittedAnswerDetailComponent implements OnInit {
    dragAndDropSubmittedAnswer: IDragAndDropSubmittedAnswer;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ dragAndDropSubmittedAnswer }) => {
            this.dragAndDropSubmittedAnswer = dragAndDropSubmittedAnswer;
        });
    }

    previousState() {
        window.history.back();
    }
}
