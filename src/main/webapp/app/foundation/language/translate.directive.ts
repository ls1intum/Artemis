import { DestroyRef, Directive, ElementRef, OnInit, effect, inject, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { translationNotFoundMessage } from 'app/core/config/translation.config';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import DOMPurify from 'dompurify';

/**
 * The markup a translation may contain: simple inline formatting and links. Images, styles, forms and other elements are removed,
 * and so are all attributes except the ones that links and the formatting classes need.
 */
const TRANSLATION_SANITIZE_CONFIG = {
    ALLOWED_TAGS: ['a', 'b', 'strong', 'em', 'i', 'u', 'br', 'code', 'span', 'p', 'small', 'sub', 'sup'],
    ALLOWED_ATTR: ['href', 'target', 'rel', 'class'],
    ALLOW_DATA_ATTR: false,
    ALLOW_ARIA_ATTR: false,
    RETURN_DOM_FRAGMENT: true as const,
};

/**
 * Escapes the characters that start markup, so that a value that is inserted into a translation is shown as text.
 */
function escapeHtml(value: string): string {
    return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

/**
 * Escapes the string values that are inserted into a translation, as they are rendered as markup. The markup of the translation itself is not touched.
 */
function escapeTranslateValues(values?: { [key: string]: unknown }): { [key: string]: unknown } | undefined {
    if (!values) {
        return values;
    }
    const escapedValues = deepClone(values);
    const visited = new WeakSet<object>();
    const escapeValue = (value: unknown): unknown => {
        if (typeof value === 'string') {
            return escapeHtml(value);
        }
        // ngx-translate resolves nested properties and joins arrays. Preserve custom value objects such as dates.
        if (value && typeof value === 'object' && (Array.isArray(value) || Object.getPrototypeOf(value) === Object.prototype || Object.getPrototypeOf(value) === null)) {
            if (!visited.has(value)) {
                visited.add(value);
                const record = value as Record<string, unknown>;
                for (const key of Object.keys(record)) {
                    record[key] = escapeValue(record[key]);
                }
            }
        }
        return value;
    };
    return escapeValue(escapedValues) as { [key: string]: unknown };
}

/**
 * A wrapper directive on top of the translate pipe as the inbuilt translate directive from ngx-translate is too verbose and buggy
 */
@Directive({
    selector: '[jhiTranslate]',
})
export class TranslateDirective implements OnInit {
    private el = inject<ElementRef<HTMLElement>>(ElementRef);
    private translateService = inject(TranslateService);
    private destroyRef = inject(DestroyRef);

    readonly jhiTranslate = input<string>();
    readonly translateValues = input<{ [key: string]: unknown }>();

    constructor() {
        // Re-render whenever the key or interpolation values change.
        effect(() => this.getTranslation());
    }

    ngOnInit(): void {
        // Re-translate on language / loaded-translation changes. Subscribed in ngOnInit (not the constructor) so that
        // merely constructing the directive does not touch the TranslateService observables — some component specs use
        // partial TranslateService doubles and never trigger change detection.
        this.translateService.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.getTranslation());
        this.translateService.onTranslationChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.getTranslation());
    }

    private getTranslation(): void {
        const key = this.jhiTranslate();
        // ngx-translate's get() throws synchronously ('Parameter "key" is required and cannot be empty') when the
        // key is empty or undefined. Because it throws before returning the Observable, the error handler below
        // never sees it — the exception escapes during change detection. A binding such as [jhiTranslate]="someValue"
        // can legitimately evaluate to undefined for a tick before its backing value is assigned (or to '' via a
        // `?? ''` fallback), so treat an empty key as "nothing to translate" and clear the element instead of
        // letting the throw spam the console and abort the surrounding view's render.
        if (!key) {
            this.el.nativeElement.textContent = '';
            return;
        }
        this.translateService
            .get(key, escapeTranslateValues(this.translateValues()))
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (value) => {
                    // Insert only the sanitized DOM fragment. Translations may legitimately contain benign markup
                    // (e.g. <a>, <strong>), but ngx-translate does NOT HTML-escape interpolated `translateValues`,
                    // so a user-controlled value (course/exercise/exam title, user name, ...) could otherwise inject
                    // markup here. DOMPurify keeps the simple inline markup of TRANSLATION_SANITIZE_CONFIG and strips everything else.
                    this.el.nativeElement.replaceChildren(DOMPurify.sanitize(value, TRANSLATION_SANITIZE_CONFIG));
                },
                // Render the not-found fallback on a genuine stream error (the common missing-key case already
                // flows through `next` via ngx-translate's MissingTranslationHandler). textContent avoids markup injection.
                error: () => {
                    this.el.nativeElement.textContent = `${translationNotFoundMessage}[${key}]`;
                },
            });
    }
}
