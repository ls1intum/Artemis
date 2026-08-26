import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DynamicDialogConfig } from 'primeng/dynamicdialog';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

/**
 * A reactive title for PrimeNG dynamic dialogs. PrimeNG's `header` string is resolved once on open, so it never
 * re-translates on a language switch; passing this via `templates.header` renders through `jhiTranslate` instead.
 * Reads `data.headerKey` / `data.headerParams` from the dialog config.
 */
@Component({
    selector: 'jhi-dialog-translate-header',
    // The `p-dialog-title` class is what PrimeNG applies to its default title span; a custom header template replaces
    // that span, so we re-apply the class here to keep the dialog title's font weight/size (otherwise it renders smaller).
    template: `<span class="p-dialog-title" [jhiTranslate]="headerKey" [translateValues]="headerParams"></span>`,
    imports: [TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogTranslateHeaderComponent {
    private readonly config = inject(DynamicDialogConfig);

    protected readonly headerKey: string = this.config.data?.headerKey ?? '';
    protected readonly headerParams: { [key: string]: unknown } | undefined = this.config.data?.headerParams;
}
