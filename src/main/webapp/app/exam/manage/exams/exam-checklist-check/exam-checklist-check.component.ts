import { Component, Input, OnInit } from '@angular/core';

@Component({
    selector: 'jhi-exam-checklist-check',
    templateUrl: './exam-checklist-check.component.html',
})
export class ExamChecklistCheckComponent implements OnInit {
    @Input() checkAttribute = false;

    constructor() {}

    ngOnInit(): void {}
}
