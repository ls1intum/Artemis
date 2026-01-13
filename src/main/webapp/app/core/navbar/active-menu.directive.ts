import { DestroyRef, Directive, ElementRef, OnInit, Renderer2, inject, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LangChangeEvent, TranslateService } from '@ngx-translate/core';

@Directive({ selector: '[jhiActiveMenu]' })
export class ActiveMenuDirective implements OnInit {
    private readonly element = inject(ElementRef);
    private readonly renderer = inject(Renderer2);
    private readonly translateService = inject(TranslateService);
    private readonly destroyRef = inject(DestroyRef);

    readonly jhiActiveMenu = input<string>();

    ngOnInit() {
        this.translateService.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((event: LangChangeEvent) => {
            this.updateActiveFlag(event.lang);
        });
        this.updateActiveFlag(this.translateService.getCurrentLang());
    }

    updateActiveFlag(selectedLanguage: string) {
        if (this.jhiActiveMenu() === selectedLanguage) {
            this.renderer.addClass(this.element.nativeElement, 'active');
        } else {
            this.renderer.removeClass(this.element.nativeElement, 'active');
        }
    }
}
