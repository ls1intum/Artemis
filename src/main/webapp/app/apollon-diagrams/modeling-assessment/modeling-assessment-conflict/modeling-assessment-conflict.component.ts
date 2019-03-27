import {Component, Input, OnInit} from '@angular/core';

@Component({
    selector: 'jhi-modeling-assessment-conflict',
    templateUrl: './modeling-assessment-conflict.component.html',
    styleUrls: ['./modeling-assessment-conflict.component.scss']
})
export class ModelingAssessmentConflictComponent implements OnInit {
    @Input() conflicts;

    conflictIndex =0;

    constructor() {

    }

    ngOnInit() {
    }

}
