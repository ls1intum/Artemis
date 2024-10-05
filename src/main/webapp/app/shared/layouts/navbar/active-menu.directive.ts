import { Directive, ElementRef, Input, OnInit, Renderer2, inject } from '@angular/core';
import { LangChangeEvent, TranslateService } from '@ngx-translate/core';

@Directive({
    selector: '[jhiActiveMenu]',
})
export class ActiveMenuDirective implements OnInit {
    private element = inject(ElementRef);
    private renderer = inject(Renderer2);
    private translateService = inject(TranslateService);

    @Input() jhiActiveMenu: string;

    ngOnInit() {
        this.translateService.onLangChange.subscribe((event: LangChangeEvent) => {
            this.updateActiveFlag(event.lang);
        });
        this.updateActiveFlag(this.translateService.currentLang);
    }

    updateActiveFlag(selectedLanguage: string) {
        if (this.jhiActiveMenu === selectedLanguage) {
            this.renderer.addClass(this.element.nativeElement, 'active');
        } else {
            this.renderer.removeClass(this.element.nativeElement, 'active');
        }
    }
}
