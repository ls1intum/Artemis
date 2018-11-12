import { Component, Input, OnInit } from '@angular/core';
import { TextAssessment } from 'app/entities/text-assessments/text-assessments.model';

@Component({
    selector: 'jhi-text-assessment-detail',
    templateUrl: './text-assessment-detail.component.html',
    styleUrls: ['./text-assessment-detail.component.scss']
})
export class TextAssessmentDetailComponent implements OnInit {
    @Input()
    public assessment: TextAssessment;

    constructor() {}

    ngOnInit() {}
}
