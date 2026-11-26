import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { IrisSolutionChoice, SOLUTION_CHOICE_LABEL_KEYS } from 'app/iris/shared/entities/iris-solution-choice.model';

/** The buttons of an offer; the first one is the suggested choice. Once chosen, only the chosen button remains. */
@Component({
    selector: 'jhi-iris-solution-choice',
    imports: [TumAetUiButtonDirective, TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
        <div class="flex flex-wrap gap-2" data-testid="iris-solution-choice">
            @for (option of shownChoices(); track option; let first = $first) {
                <button
                    type="button"
                    tumAetUiButton
                    severity="primary"
                    [variant]="first ? 'solid' : 'outlined'"
                    size="small"
                    data-button-intent="workflow"
                    [attr.data-testid]="'iris-choice-' + option"
                    [disabled]="disabled() || selected() !== undefined"
                    [attr.aria-pressed]="selected() === option"
                    (click)="choose(option)"
                    [jhiTranslate]="labelKeys[option]"
                ></button>
            }
        </div>
    `,
})
export class IrisSolutionChoiceComponent {
    readonly choices = input.required<IrisSolutionChoice[]>();
    readonly disabled = input(false);
    readonly selected = input<IrisSolutionChoice>();
    readonly choice = output<IrisSolutionChoice>();

    protected readonly labelKeys = SOLUTION_CHOICE_LABEL_KEYS;
    protected readonly shownChoices = computed(() => {
        const selected = this.selected();
        return selected === undefined ? this.choices() : [selected];
    });

    protected choose(choice: IrisSolutionChoice): void {
        this.choice.emit(choice);
    }
}
