import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IDragAndDropQuestion } from 'app/shared/model/drag-and-drop-question.model';

@Component({
    selector: 'jhi-drag-and-drop-question-detail',
    templateUrl: './drag-and-drop-question-detail.component.html'
})
export class DragAndDropQuestionDetailComponent implements OnInit {
    dragAndDropQuestion: IDragAndDropQuestion;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ dragAndDropQuestion }) => {
            this.dragAndDropQuestion = dragAndDropQuestion;
        });
    }

    previousState() {
        window.history.back();
    }
}
