import { Component, input, model, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { faTrashCan } from '@fortawesome/free-regular-svg-icons';
import { TumAetUiButtonDirective, TumAetUiDialogComponent, TumAetUiMessageComponent } from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** A translation key with its parameters. */
export interface SecurityTranslatable {
    key: string;
    params?: Record<string, string | number>;
}

/** One line of the details of the item a destructive action affects, e.g. the path of a row. */
export interface SecurityConfirmDetail {
    labelKey: string;
    value: string;
}

/**
 * Asks before a destructive action of the Advanced view: deleting a permission or a policy, removing a test case or a test class, or discarding the unsaved
 * changes. It names what is affected, warns what changes for student code, and states that nothing reaches the repository until the next commit.
 */
@Component({
    selector: 'jhi-security-confirm-dialog',
    templateUrl: './security-confirm-dialog.component.html',
    styleUrl: './security-policies.scss',
    imports: [TumAetUiDialogComponent, TumAetUiButtonDirective, TumAetUiMessageComponent, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
})
export class SecurityConfirmDialogComponent {
    protected readonly faTrashCan = faTrashCan;
    protected readonly faTriangleExclamation = faTriangleExclamation;

    readonly visible = model(false);
    readonly header = input.required<SecurityTranslatable>();
    readonly question = input.required<SecurityTranslatable>();
    readonly details = input<SecurityConfirmDetail[]>([]);
    /** The affected entry, shown boxed, e.g. a test case. */
    readonly entry = input<string | undefined>(undefined);
    readonly warning = input<SecurityTranslatable | undefined>(undefined);
    readonly note = input<SecurityTranslatable | undefined>(undefined);
    readonly confirmLabel = input.required<string>();

    readonly confirmed = output<void>();

    protected confirm(): void {
        this.visible.set(false);
        this.confirmed.emit();
    }
}
