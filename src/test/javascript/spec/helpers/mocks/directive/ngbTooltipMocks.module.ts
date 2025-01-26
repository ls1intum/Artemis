import { Component, Directive, EventEmitter, Input, NgModule, Output } from '@angular/core';

@Component({
    selector: 'ngb-tooltip-window',
    template: ``,
})
export class NgbTooltipWindowMockComponent {
    @Input() animation: boolean;
    @Input() id: string;
    @Input() tooltipClass: string;
}

@Directive({
    selector: '[ngbTooltip]',
    exportAs: 'ngbTooltip',
})
export class NgbTooltipMockDirective {
    @Input() animation: boolean;
    @Input() autoClose: boolean | 'inside' | 'outside';
    @Input() placement: any;
    @Input() popperOptions: (options: Partial<any>) => Partial<any>;
    @Input() triggers: string;
    @Input() positionTarget?: string | HTMLElement;
    @Input() container: string;
    @Input() disableTooltip: boolean;
    @Input() tooltipClass: string;
    @Input() openDelay: number;
    @Input() closeDelay: number;
    @Input() ngbTooltip: any;

    @Output() shown = new EventEmitter();
    @Output() hidden = new EventEmitter();
}

@NgModule({
    exports: [NgbTooltipMockDirective, NgbTooltipWindowMockComponent],
    imports: [NgbTooltipMockDirective, NgbTooltipWindowMockComponent],
})
export class NgbTooltipMocksModule {}
