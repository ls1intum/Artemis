import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { IrisSolutionChoice } from 'app/iris/shared/entities/iris-solution-choice.model';

@Component({
    selector: 'jhi-iris-solution-choice',
    imports: [TumAetUiButtonDirective, TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
        <div class="flex flex-wrap gap-2" data-testid="iris-solution-choice">
            <button
                type="button"
                tumAetUiButton
                severity="primary"
                size="small"
                data-button-intent="workflow"
                data-testid="iris-try-myself"
                [disabled]="disabled() || selected() !== undefined"
                [attr.aria-pressed]="selected() === 'TRY_MYSELF'"
                (click)="choose('TRY_MYSELF')"
                jhiTranslate="artemisApp.iris.solutionChoice.tryMyself"
            ></button>
            <button
                type="button"
                tumAetUiButton
                severity="primary"
                variant="outlined"
                size="small"
                data-button-intent="workflow"
                data-testid="iris-show-solution"
                [disabled]="disabled() || selected() !== undefined"
                [attr.aria-pressed]="selected() === 'SHOW_SOLUTION'"
                (click)="choose('SHOW_SOLUTION')"
                jhiTranslate="artemisApp.iris.solutionChoice.showSolution"
            ></button>
        </div>
    `,
})
export class IrisSolutionChoiceComponent {
    readonly disabled = input(false);
    readonly selected = input<IrisSolutionChoice>();
    readonly choice = output<IrisSolutionChoice>();

    protected choose(choice: IrisSolutionChoice): void {
        if (!this.disabled() && this.selected() === undefined) this.choice.emit(choice);
    }
}
