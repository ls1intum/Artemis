import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TumAetUiDialogConfig } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

/**
 * A reactive title for TUM UI dynamic dialogs. The dialog's `header` string is resolved once on open, so it never
 * re-translates on a language switch; passing this via `templates.header` renders through `jhiTranslate` instead.
 * Reads `data.headerKey` / `data.headerParams` from the dialog config.
 */
@Component({
    selector: 'jhi-dialog-translate-header',
    template: `<span class="text-xl font-semibold" [jhiTranslate]="headerKey" [translateValues]="headerParams"></span>`,
    imports: [TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogTranslateHeaderComponent {
    private readonly config = inject(TumAetUiDialogConfig<{ headerKey?: string; headerParams?: { [key: string]: unknown } }>);

    protected readonly headerKey: string = this.config.data?.headerKey ?? '';
    protected readonly headerParams: { [key: string]: unknown } | undefined = this.config.data?.headerParams;
}
