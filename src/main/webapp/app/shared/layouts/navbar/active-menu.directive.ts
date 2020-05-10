import { Directive, ElementRef, Input, OnInit, Renderer2 } from '@angular/core';
import { LangChangeEvent, TranslateService } from '@ngx-translate/core';

@Directive({
    selector: '[jhiActiveMenu]',
})
export class ActiveMenuDirective implements OnInit {
    @Input() jhiActiveMenu: string;

    constructor(private el: ElementRef, private renderer: Renderer2, private translateService: TranslateService) {}

    /**
     * Lifecycle function which is called after the component is created.
     */
    ngOnInit() {
        this.translateService.onLangChange.subscribe((event: LangChangeEvent) => {
            this.updateActiveFlag(event.lang);
        });
        this.updateActiveFlag(this.translateService.currentLang);
    }

    /**
     * @function updateActiveFlag
     * Sets the active flag on the active components depending on the selectedLanguage and removes it frominactive ones
     * @param selectedLanguage { string }
     */
    updateActiveFlag(selectedLanguage: string) {
        if (this.jhiActiveMenu === selectedLanguage) {
            this.renderer.addClass(this.el.nativeElement, 'active');
        } else {
            this.renderer.removeClass(this.el.nativeElement, 'active');
        }
    }
}
