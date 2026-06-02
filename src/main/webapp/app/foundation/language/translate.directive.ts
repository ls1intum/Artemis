import { DestroyRef, Directive, ElementRef, OnInit, effect, inject, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { translationNotFoundMessage } from 'app/core/config/translation.config';

/**
 * A wrapper directive on top of the translate pipe as the inbuilt translate directive from ngx-translate is too verbose and buggy
 */
@Directive({
    selector: '[jhiTranslate]',
})
export class TranslateDirective implements OnInit {
    private el = inject(ElementRef);
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
            .get(key, this.translateValues())
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (value) => {
                    this.el.nativeElement.innerHTML = value;
                },
                // Render the not-found fallback on a genuine stream error (the common missing-key case already
                // flows through `next` via ngx-translate's MissingTranslationHandler). textContent avoids markup injection.
                error: () => {
                    this.el.nativeElement.textContent = `${translationNotFoundMessage}[${key}]`;
                },
            });
    }
}
