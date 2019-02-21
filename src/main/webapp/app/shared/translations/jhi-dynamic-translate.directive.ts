import { Input, Directive, OnChanges } from '@angular/core';
import { JhiTranslateDirective } from 'ng-jhipster/';

/**
 * A wrapper directive on top of the translate pipe as the inbuilt translate directive from ngx-translate is too verbose and buggy
 */
@Directive({
    selector: '[jhiDynamicTranslate]'
})
export class JhiDynamicTranslateDirective extends JhiTranslateDirective implements OnChanges {
    @Input() jhiDynamicTranslate: string;
    @Input() dynamicLookup: any;

    ngOnChanges() {
        this.jhiTranslate = [this.jhiDynamicTranslate, this.dynamicLookup].join('.');
        super.ngOnChanges();
    }
}
