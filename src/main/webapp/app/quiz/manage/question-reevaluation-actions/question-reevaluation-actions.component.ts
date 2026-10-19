import { ChangeDetectionStrategy, Component, output } from '@angular/core';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronUp, faUndo } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'div[jhi-question-reevaluation-actions]',
    imports: [NgbTooltip, FaIconComponent, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
        <button
            id="move-up-button"
            class="btn btn-outline-secondary"
            type="button"
            (click)="moveUp.emit()"
            [ngbTooltip]="'artemisApp.quizExercise.re-evaluate.moveUp' | artemisTranslate"
        >
            <fa-icon [icon]="faChevronUp" />
        </button>
        <button
            id="move-down-button"
            class="btn btn-outline-secondary"
            type="button"
            (click)="moveDown.emit()"
            [ngbTooltip]="'artemisApp.quizExercise.re-evaluate.moveDown' | artemisTranslate"
        >
            <fa-icon [icon]="faChevronDown" />
        </button>
        <button
            id="reset-button"
            class="btn btn-outline-secondary"
            type="button"
            (click)="resetQuestion.emit()"
            [ngbTooltip]="'artemisApp.quizExercise.re-evaluate.resetQuestion' | artemisTranslate"
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
