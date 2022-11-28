import { Component, Input } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/entities/exercise.model';
import { Authority } from 'app/shared/constants/authority.constants';

@Component({
    selector: 'jhi-presentation-score-checkbox',
    template: `
        <ng-container *jhiHasAnyAuthority="[Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR]">
            <div class="form-group" *ngIf="this.showPresentationScoreCheckbox()">
                <div class="form-check custom-control custom-checkbox">
                    <input
                        type="checkbox"
                        class="form-check-input custom-control-input"
                        id="field_presentationScoreEnabled"
                        name="presentationScoreEnabled"
                        [ngModel]="exercise.presentationScoreEnabled"
                        (ngModelChange)="exercise.presentationScoreEnabled = !exercise.presentationScoreEnabled"
                    />
                    <label class="form-check-label custom-control-label" for="field_presentationScoreEnabled" jhiTranslate="artemisApp.exercise.presentationScoreEnabled.title"
                        >Presentation Score Enabled</label
                    >
                    <fa-icon
                        [icon]="faQuestionCircle"
                        class="text-secondary"
                        ngbTooltip="{{ 'artemisApp.exercise.presentationScoreEnabled.description' | artemisTranslate }}"
                    ></fa-icon>
                </div>
            </div>
        </ng-container>
    `,
    styleUrls: ['../../shared/exercise/_exercise-update.scss'],
})
export class PresentationScoreComponent {
    @Input() exercise: Exercise;

    Authority = Authority;
    // Icons
    faQuestionCircle = faQuestionCircle;

    showPresentationScoreCheckbox(): boolean {
        return !!(this.exercise.course && this.exercise.course.presentationScore !== 0);
    }
}
