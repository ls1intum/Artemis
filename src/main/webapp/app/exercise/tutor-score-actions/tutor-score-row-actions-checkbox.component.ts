import { Component, input } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { IS_AT_LEAST_INSTRUCTOR } from 'app/foundation/constants/authority.constants';
import { HasAnyAuthorityDirective } from 'app/foundation/auth/has-any-authority.directive';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/**
 * Instructor-only checkbox letting instructors opt in, per exercise, to show the exercise-scores page row actions
 * to users who are at least tutor (instead of only instructors).
 */
@Component({
    selector: 'jhi-tutor-score-row-actions-checkbox',
    template: `
        <ng-container *jhiHasAnyAuthority="IS_AT_LEAST_INSTRUCTOR">
            <div class="form-group">
                <div class="form-check custom-control custom-checkbox">
                    <input
                        type="checkbox"
                        class="form-check-input custom-control-input"
                        id="field_allowTutorScoreRowActions"
                        name="allowTutorScoreRowActions"
                        [ngModel]="exercise().allowTutorScoreRowActions"
                        (ngModelChange)="exercise().allowTutorScoreRowActions = $event"
                    />
                    <label
                        class="form-check-label custom-control-label"
                        for="field_allowTutorScoreRowActions"
                        jhiTranslate="artemisApp.exercise.allowTutorScoreRowActions.title"
                    ></label>
                    <fa-icon [icon]="faQuestionCircle" class="text-secondary" ngbTooltip="{{ 'artemisApp.exercise.allowTutorScoreRowActions.description' | artemisTranslate }}" />
                </div>
            </div>
        </ng-container>
    `,
    imports: [HasAnyAuthorityDirective, FormsModule, TranslateDirective, FaIconComponent, NgbTooltip, ArtemisTranslatePipe],
})
export class TutorScoreRowActionsCheckboxComponent {
    protected readonly faQuestionCircle = faQuestionCircle;

    protected readonly IS_AT_LEAST_INSTRUCTOR = IS_AT_LEAST_INSTRUCTOR;

    readonly exercise = input<Exercise>(undefined!);
}
