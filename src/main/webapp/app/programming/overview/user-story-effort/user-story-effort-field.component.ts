import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TumUiButtonDirective, TumUiInputDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/**
 * The contents of one reported-effort information box: the number when it has been reported, a placeholder when it has
 * not, and an inline editor while the student is entering one.
 *
 * Purely presentational. The surrounding box owns both the click that starts editing and the orange border that flags a
 * missing value, so the whole box is the target rather than just this text; and the header owns the value and the
 * saving, so both effort boxes share one request.
 */
@Component({
    selector: 'jhi-user-story-effort-field',
    templateUrl: './user-story-effort-field.component.html',
    styleUrl: './user-story-effort-field.component.scss',
    imports: [FormsModule, TumUiButtonDirective, TumUiInputDirective, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserStoryEffortFieldComponent {
    readonly value = input<number | undefined>(undefined);
    /** Controlled by the header, which starts editing when its box is clicked. */
    readonly editing = input<boolean>(false);

    readonly valueChange = output<number | undefined>();
    readonly cancelled = output<void>();

    protected readonly draft = signal<number | undefined>(undefined);

    protected readonly displayValue = computed(() => this.value());

    constructor() {
        // Reset the draft whenever editing starts, or when the stored value changes underneath it.
        effect(() => {
            const value = this.value();
            const editing = this.editing();
            untracked(() => {
                if (editing) {
                    this.draft.set(value);
                }
            });
        });
    }

    protected save(): void {
        this.valueChange.emit(this.draft() ?? undefined);
    }

    protected cancel(): void {
        this.draft.set(this.value());
        this.cancelled.emit();
    }
}
