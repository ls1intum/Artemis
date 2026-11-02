import { ChangeDetectionStrategy, Component, output } from '@angular/core';
import { TumAetUiButtonDirective, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronUp, faUndo } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'div[jhi-question-reevaluation-actions]',
    imports: [TumAetUiButtonDirective, TumAetUiTooltipDirective, FaIconComponent, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
        <button
            id="move-up-button"
            tumAetUiButton
            severity="secondary"
            variant="outlined"
            class="py-1.5!"
            type="button"
            (click)="moveUp.emit()"
            [tumAetUiTooltip]="'artemisApp.quizExercise.re-evaluate.moveUp' | artemisTranslate"
            [attr.aria-label]="'artemisApp.quizExercise.re-evaluate.moveUp' | artemisTranslate"
        >
            <fa-icon [icon]="faChevronUp" />
        </button>
        <button
            id="move-down-button"
            tumAetUiButton
            severity="secondary"
            variant="outlined"
            class="py-1.5!"
            type="button"
            (click)="moveDown.emit()"
            [tumAetUiTooltip]="'artemisApp.quizExercise.re-evaluate.moveDown' | artemisTranslate"
            [attr.aria-label]="'artemisApp.quizExercise.re-evaluate.moveDown' | artemisTranslate"
        >
            <fa-icon [icon]="faChevronDown" />
        </button>
        <button
            id="reset-button"
            tumAetUiButton
            severity="secondary"
            variant="outlined"
            class="py-1.5!"
            type="button"
            (click)="resetQuestion.emit()"
            [tumAetUiTooltip]="'artemisApp.quizExercise.re-evaluate.resetQuestion' | artemisTranslate"
            [attr.aria-label]="'artemisApp.quizExercise.re-evaluate.resetQuestion' | artemisTranslate"
        >
            <fa-icon [icon]="faUndo" />
        </button>
    `,
})
export class QuestionReevaluationActionsComponent {
    readonly moveUp = output<void>();
    readonly moveDown = output<void>();
    readonly resetQuestion = output<void>();
    protected readonly faChevronUp = faChevronUp;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faUndo = faUndo;
}
