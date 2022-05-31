import { Component, Input } from '@angular/core';
import { Submission } from 'app/entities/submission.model';

@Component({
    selector: 'jhi-assesment-progress-label',
    templateUrl: './assessment-progress-label.html',
})
export class AssessmentProgressLabelComponent {
    @Input()
    submissions: Submission[] = [];
    @Input()
    numberAssessedSubmissions: number;
}
