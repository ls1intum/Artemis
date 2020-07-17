import { Component, Input } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-exam-points-summary',
    templateUrl: './exam-points-summary.component.html',
})
export class ExamPointsSummaryComponent {
    @Input() exercises: Exercise[];

    /**
     * Checks if results are part of the received exercises array.
     */
    hasResults(): boolean {
        return true;
    }
}
