import { Directive, ElementRef, OnDestroy, OnInit, effect, inject, input } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { translationNotFoundMessage } from 'app/core/config/translation.config';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

/**
 * A wrapper directive on top of the translate pipe as the inbuilt translate directive from ngx-translate is too verbose and buggy
 */
@Directive({
    selector: '[jhiTranslate]',
})
export class TranslateDirective implements OnInit, OnDestroy {
    private el = inject(ElementRef);
    private translateService = inject(TranslateService);

    readonly jhiTranslate = input<string>();
    readonly translateValues = input<{ [key: string]: unknown }>();

    private readonly directiveDestroyed = new Subject<void>();

    constructor() {
        // Re-translate whenever the key or the interpolation values change (replaces ngOnChanges).
        effect(() => {
            // Track both inputs so the effect re-runs on either change.
            this.jhiTranslate();
            this.translateValues();
            this.getTranslation();
        });
    }

    ngOnInit(): void {
        this.translateService.onLangChange.pipe(takeUntil(this.directiveDestroyed)).subscribe(() => {
            this.getTranslation();
        });
        this.translateService.onTranslationChange.pipe(takeUntil(this.directiveDestroyed)).subscribe(() => {
            this.getTranslation();
        });
    }

    ngOnDestroy(): void {
        this.directiveDestroyed.next();
        this.directiveDestroyed.complete();
    }

    private getTranslation(): void {
        const key = this.jhiTranslate();
        // ngx-translate's get() throws synchronously ('Parameter "key" is required and cannot be empty') when the
        // key is empty or undefined. Because it throws before returning the Observable, the error handler below
        // never sees it — the exception escapes ngOnChanges/ngOnInit during change detection. A binding such as
        // [jhiTranslate]="someValue" can legitimately evaluate to undefined for a tick before its backing value
        // is assigned (or to '' via a `?? ''` fallback), so treat an empty key as "nothing to translate" and
        // clear the element instead of letting the throw spam the console and abort the surrounding view's render.
        if (!key) {
            this.el.nativeElement.textContent = '';
            return;
        }
        this.translateService
            .get(key, this.translateValues())
            .pipe(takeUntil(this.directiveDestroyed))
            .subscribe({
                next: (value) => {
                    this.el.nativeElement.innerHTML = value;
                },
                error: () => `${translationNotFoundMessage}[${key}]`,
            });
    }
}
