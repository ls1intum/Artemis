import { AfterViewInit, Directive, ElementRef, HostListener, OnDestroy, OnInit, Renderer2, effect, inject, input } from '@angular/core';

// NOTE: this code was taken from https://github.com/sollenne/angular-fittext because the repository was not maintained any more since June 2018

@Directive({ selector: '[fitText]' })
export class FitTextDirective implements AfterViewInit, OnInit, OnDestroy {
    private renderer = inject(Renderer2);

    fitText = input(true);
    compression = input(1);
    activateOnResize = input(true);
    minFontSize = input<number | 'inherit'>(0);
    maxFontSize = input<number | 'inherit'>(Number.POSITIVE_INFINITY);
    delay = input(100);
    innerHTML = input<any>();
    fontUnit = input<'px' | 'em' | string>('px');

    private readonly fitTextElement: HTMLElement;
    private readonly computed: CSSStyleDeclaration;
    private readonly newlines: number;
    private readonly lineHeight: string;
    private readonly display: string;
    private fitTextParent: HTMLElement;
    private fitTextMinFontSize: number;
    private fitTextMaxFontSize: number;
    private calcSize = 10;
    private resizeTimeout: NodeJS.Timeout;

    private isFirstCompression = true;
    private isFirstInnerHTML = true;

    constructor() {
        const el = inject(ElementRef);

        this.fitTextElement = el.nativeElement;
        this.fitTextParent = this.fitTextElement.parentElement!;
        this.computed = window.getComputedStyle(this.fitTextElement);
        this.newlines = this.fitTextElement.childElementCount > 0 ? this.fitTextElement.childElementCount : 1;
        this.lineHeight = this.computed.lineHeight;
        this.display = this.computed.display;

        effect(() => {
            this.compression();
            if (this.isFirstCompression) {
                this.isFirstCompression = false;
            } else {
                this.setFontSize(0);
            }
        });

        effect(() => {
            const html = this.innerHTML();
            if (html !== undefined) {
                this.fitTextElement.innerHTML = html;
                if (this.isFirstInnerHTML) {
                    this.isFirstInnerHTML = false;
                } else {
                    this.setFontSize(0);
                }
            }
        });
    }

    @HostListener('window:resize')
    public onWindowResize = (): void => {
        if (this.activateOnResize()) {
            this.setFontSize();
        }
    };

    public ngOnInit() {
        const minFontSize = this.minFontSize();
        const maxFontSize = this.maxFontSize();
        this.fitTextMinFontSize = minFontSize === 'inherit' ? Number(this.computed.fontSize) : minFontSize!;
        this.fitTextMaxFontSize = maxFontSize === 'inherit' ? Number(this.computed.fontSize) : maxFontSize!;
    }

    public ngAfterViewInit() {
        this.setFontSize(0);
    }

    public ngOnDestroy() {
        clearTimeout(this.resizeTimeout);
    }

    private setFontSize = (delay: number = this.delay()): void => {
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
                    this.compression(),
                this.fitTextMaxFontSize,
            ),
            this.fitTextMinFontSize,
        );
    };

    private setStyles = (fontSize: number, lineHeight: number | string, display: string): void => {
        this.renderer.setStyle(this.fitTextElement, 'fontSize', fontSize.toString() + this.fontUnit());
        this.renderer.setStyle(this.fitTextElement, 'lineHeight', lineHeight.toString());
        this.renderer.setStyle(this.fitTextElement, 'display', display);
    };
}
