import { Component, Input } from '@angular/core';
import { Exercise } from 'app/entities/exercise';

@Component({
    selector: 'jhi-presentation-score-checkbox',
    template: `
        <ng-container *jhiHasAnyAuthority="['ROLE_ADMIN', 'ROLE_INSTRUCTOR']">
            <div class="form-group" *ngIf="exercise.course && exercise.course.presentationScore !== 0">
                <div class="form-check custom-control custom-checkbox">
                    <input
                        type="checkbox"
                        class="form-check-input custom-control-input"
                        id="field_presentationScoreEnabled"
                        name="presentationScoreEnabled"
                        [ngModel]="exercise.presentationScoreEnabled"
                        (ngModelChange)="exercise.presentationScoreEnabled = !exercise.presentationScoreEnabled"
                    />
                    <label class="form-check-label custom-control-label" for="field_presentationScoreEnabled" jhiTranslate="artemisApp.exercise.presentationScoreEnabled"
                        >Presentation Score Enabled</label
                    >
                </div>
            </div>
        </ng-container>
    `,
    styles: [],
})
export class PresentationScoreComponent {
    @Input() exercise: Exercise;
}
