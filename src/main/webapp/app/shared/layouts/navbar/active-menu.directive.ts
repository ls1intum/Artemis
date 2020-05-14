import { Directive, ElementRef, Input, OnInit, Renderer2 } from '@angular/core';
import { LangChangeEvent, TranslateService } from '@ngx-translate/core';

@Directive({
    selector: '[jhiActiveMenu]',
})
export class ActiveMenuDirective implements OnInit {
    @Input() jhiActiveMenu: string;

    constructor(private el: ElementRef, private renderer: Renderer2, private translateService: TranslateService) {}

    /**
     * Lifecycle function which is called on initialisation. Triggers {@link updateActiveFlag} for the current language.
     * Subscribes to {@link LangChangeEvent} and triggers {@link updateActiveFlag} when an event is received.
     */
    ngOnInit() {
        this.translateService.onLangChange.subscribe((event: LangChangeEvent) => {
            this.updateActiveFlag(event.lang);
        });
        this.updateActiveFlag(this.translateService.currentLang);
    }

    /** Sets the {@link active} flag on the active components depending on the {@param selectedLanguage} and removes it from inactive ones.
     * @method
     *
     * @param selectedLanguage {string}
     */
    updateActiveFlag(selectedLanguage: string) {
        if (this.jhiActiveMenu === selectedLanguage) {
            this.renderer.addClass(this.el.nativeElement, 'active');
        } else {
            this.renderer.removeClass(this.el.nativeElement, 'active');
        }
    }
}
