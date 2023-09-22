import {
    ApplicationRef,
    ChangeDetectorRef,
    Directive,
    ElementRef,
    Inject,
    Injector,
    Input,
    NgZone,
    OnDestroy,
    OnInit,
    Renderer2,
    TemplateRef,
    ViewContainerRef,
} from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { NgbPopover, NgbPopoverConfig } from '@ng-bootstrap/ng-bootstrap';

@Directive({
    selector: '[jhiStickyPopover]',
})
export class StickyPopoverDirective extends NgbPopover implements OnInit, OnDestroy {
    @Input() jhiStickyPopover: TemplateRef<any>;

    popoverTitle: string;

    triggers: string;
    container: string;
    canClosePopover: boolean;

    private closeTimeout: any;
    private clickInPopover: boolean = false;

    toggle(): void {
        super.toggle();
    }

    isOpen(): boolean {
        return super.isOpen();
    }

    constructor(
        private _elRef: ElementRef,
        private _render: Renderer2,
        injector: Injector,
        private viewContainerRef: ViewContainerRef,
        config: NgbPopoverConfig,
        ngZone: NgZone,
        private changeRef: ChangeDetectorRef,
        private applicationRef: ApplicationRef,
        @Inject(DOCUMENT) _document: any,
    ) {
        super(_elRef, _render, injector, viewContainerRef, config, ngZone, _document, changeRef, applicationRef);
        this.triggers = 'manual';
        this.popoverTitle = '';
        this.container = 'body';
    }

    ngOnInit(): void {
        super.ngOnInit();
        this.ngbPopover = this.jhiStickyPopover;

        this._render.listen(this._elRef.nativeElement, 'pointerenter', () => {
            this.canClosePopover = true;
            clearTimeout(this.closeTimeout);
            if (!this.isOpen()) {
                this.open();
            }
        });

        this._render.listen(this._elRef.nativeElement, 'pointerleave', () => {
            this.closeTimeout = setTimeout(() => {
                if (this.canClosePopover) {
                    this.close();
                }
            }, 100);
        });

        this._render.listen(this._elRef.nativeElement, 'click', () => {
            this.close();
        });
    }

    ngOnDestroy(): void {
        super.ngOnDestroy();
    }

    open() {
        super.open();
        setTimeout(() => {
            const popover = window.document.querySelector('.popover');
            this._render.listen(popover, 'mouseover', () => {
                this.canClosePopover = false;
            });

            this._render.listen(popover, 'pointerleave', () => {
                this.canClosePopover = true;
                clearTimeout(this.closeTimeout);
                this.closeTimeout = setTimeout(() => {
                    if (this.canClosePopover) {
                        this.close();
                    }
                }, 250);
            });

            this._render.listen(popover, 'click', () => {
                this.clickInPopover = true;
            });
        }, 0);
    }

    close() {
        if (this.clickInPopover) {
            this.clickInPopover = false;
        } else {
            super.close();
        }
    }
}
