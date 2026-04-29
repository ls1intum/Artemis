import { Directive } from '@angular/core';

/**
 * Avoids password manager popup when applied.
 */
@Directive({
    selector: 'input[jhiPasswordManagerIgnore]',
    standalone: true,
    host: {
        '[attr.autocomplete]': '"off"',
        '[attr.data-1p-ignore]': '"true"', // 1Password
        '[attr.data-lpignore]': '"true"', // LastPass
        '[attr.data-protonpass-ignore]': '"true"', // ProtonPass
        '[attr.data-bwignore]': '"1"', // Bitwarden
    },
})
export class PasswordManagerIgnoreDirective {}
