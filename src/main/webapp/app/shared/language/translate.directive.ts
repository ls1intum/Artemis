import { Directive, ElementRef, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
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
export class TranslateDirective implements OnChanges, OnInit, OnDestroy {
    @Input() jhiTranslate!: string;
    @Input() translateValues?: { [key: string]: unknown };

    private readonly directiveDestroyed = new Subject<void>();

    constructor(private el: ElementRef, private translateService: TranslateService) {}

    ngOnInit(): void {
        this.translateService.onLangChange.pipe(takeUntil(this.directiveDestroyed)).subscribe(() => {
            this.getTranslation();
        });
        this.translateService.onTranslationChange.pipe(takeUntil(this.directiveDestroyed)).subscribe(() => {
            this.getTranslation();
        });
    }

    ngOnChanges(): void {
        this.getTranslation();
    }

    ngOnDestroy(): void {
        this.directiveDestroyed.next();
        this.directiveDestroyed.complete();
    }

    private getTranslation(): void {
        this.translateService
            .get(this.jhiTranslate, this.translateValues)
            .pipe(takeUntil(this.directiveDestroyed))
            .subscribe({
                next: (value) => {
                    this.el.nativeElement.innerHTML = value;
                },
                error: () => `${translationNotFoundMessage}[${this.jhiTranslate}]`,
            });
    }
}
