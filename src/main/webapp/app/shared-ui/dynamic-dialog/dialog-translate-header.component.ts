import { Component, inject } from '@angular/core';
import { DynamicDialogConfig } from 'primeng/dynamicdialog';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

/**
 * A reactive title for PrimeNG dynamic dialogs. PrimeNG's `header` config option is a plain string that is resolved
 * once when the dialog opens, so it does not re-translate when the user switches language while the dialog is open
 * (this app is zoneless, so the value is never re-evaluated). Passing this component via `templates.header` instead
 * renders the title through the `jhiTranslate` directive, which rewrites the DOM on every language change.
 *
 * The translation key (and optional interpolation params) are read from the dialog's `data.headerKey` /
 * `data.headerParams`.
 */
@Component({
    selector: 'jhi-dialog-translate-header',
    template: `<span [jhiTranslate]="headerKey" [translateValues]="headerParams"></span>`,
    imports: [TranslateDirective],
})
export class DialogTranslateHeaderComponent {
    private readonly config = inject(DynamicDialogConfig);

    protected readonly headerKey: string = this.config.data?.headerKey ?? '';
    protected readonly headerParams: { [key: string]: unknown } | undefined = this.config.data?.headerParams;
}
