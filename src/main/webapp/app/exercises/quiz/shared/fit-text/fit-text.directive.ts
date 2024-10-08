import { AfterViewInit, Directive, ElementRef, HostListener, Input, OnChanges, OnInit, Renderer2, SimpleChanges, inject } from '@angular/core';

// NOTE: this code was taken from https://github.com/sollenne/angular-fittext because the repository was not maintained any more since June 2018

// eslint-disable-next-line @angular-eslint/directive-selector
@Directive({ selector: '[fitText]' })
export class FitTextDirective implements AfterViewInit, OnInit, OnChanges {
    private el = inject(ElementRef);
    private renderer = inject(Renderer2);

    @Input() fitText = true;
    @Input() compression = 1;
    @Input() activateOnResize = true;
    @Input() minFontSize?: number | 'inherit' = 0;
    @Input() maxFontSize?: number | 'inherit' = Number.POSITIVE_INFINITY;
    @Input() delay = 100;
    @Input() innerHTML: any;
    @Input() fontUnit: 'px' | 'em' | string = 'px';

    private readonly fitTextElement: HTMLElement;
    private readonly computed: CSSStyleDeclaration;
    private readonly newlines: number;
    private readonly lineHeight: string;
    private readonly display: string;
    private fitTextParent: HTMLElement;
    private fitTextMinFontSize: number;
    private fitTextMaxFontSize: number;
    private calcSize = 10;
    private resizeTimeout: any;

    constructor() {
        const el = this.el;

        this.fitTextElement = el.nativeElement;
        this.fitTextParent = this.fitTextElement.parentElement!;
        this.computed = window.getComputedStyle(this.fitTextElement);
        this.newlines = this.fitTextElement.childElementCount > 0 ? this.fitTextElement.childElementCount : 1;
        this.lineHeight = this.computed.lineHeight;
        this.display = this.computed.display;
    }

    @HostListener('window:resize')
    public onWindowResize = (): void => {
        if (this.activateOnResize) {
            this.setFontSize();
        }
    };

    public ngOnInit() {
        this.fitTextMinFontSize = this.minFontSize === 'inherit' ? Number(this.computed.fontSize) : this.minFontSize!;
        this.fitTextMaxFontSize = this.maxFontSize === 'inherit' ? Number(this.computed.fontSize) : this.maxFontSize!;
    }

    public ngAfterViewInit() {
        this.setFontSize(0);
    }

    public ngOnChanges(changes: SimpleChanges) {
        if (changes['compression'] && !changes['compression'].firstChange) {
            this.setFontSize(0);
        }
        if (changes['innerHTML']) {
            this.fitTextElement.innerHTML = this.innerHTML;
            if (!changes['innerHTML'].firstChange) {
                this.setFontSize(0);
            }
        }
    }

    private setFontSize = (delay: number = this.delay): void => {
        this.resizeTimeout = setTimeout(() => {
            if (this.fitTextElement.offsetHeight * this.fitTextElement.offsetWidth !== 0) {
                // reset to default
                this.setStyles(this.calcSize, 1, 'inline-block');
                // set new
                this.setStyles(this.calculateNewFontSize(), this.lineHeight, this.display);
            }
        }, delay);
    };

    private calculateNewFontSize = (): number => {
        const ratio = (this.calcSize * this.newlines) / this.fitTextElement.offsetWidth / this.newlines;

        return Math.max(
            Math.min(
                (this.fitTextParent.offsetWidth -
                    (parseFloat(getComputedStyle(this.fitTextParent).paddingLeft) + parseFloat(getComputedStyle(this.fitTextParent).paddingRight)) -
                    6) *
                    ratio *
                    this.compression,
                this.fitTextMaxFontSize,
            ),
            this.fitTextMinFontSize,
        );
    };

    private setStyles = (fontSize: number, lineHeight: number | string, display: string): void => {
        this.renderer.setStyle(this.fitTextElement, 'fontSize', fontSize.toString() + this.fontUnit);
        this.renderer.setStyle(this.fitTextElement, 'lineHeight', lineHeight.toString());
        this.renderer.setStyle(this.fitTextElement, 'display', display);
    };
}
