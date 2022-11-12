import { AgVsRenderEvent } from './classes/ag-vs-render-event.class';
import { AgVsItemComponent } from './ag-vs-item/ag-vs-item.component';
import { Observable, Subscription } from 'rxjs';
import {
    AfterContentChecked,
    AfterViewInit,
    Component,
    ContentChildren,
    ElementRef,
    EventEmitter,
    Input,
    OnChanges,
    OnDestroy,
    OnInit,
    Output,
    QueryList,
    Renderer2,
    SimpleChanges,
    ViewChild,
} from '@angular/core';

@Component({
    selector: 'ag-virtual-scroll',
    templateUrl: './ag-virtual-scroll.component.html',
    styles: [
        `
            :host {
                display: block;
                position: relative;
                height: 100%;
                width: 100%;
                overflow-y: auto;
            }

            :host .content-height {
                width: 1px;
                opacity: 0;
            }

            :host .items-container {
                position: absolute;
                top: 0;
                left: 0;
                width: 100%;
            }

            :host::ng-deep .items-container.sticked-outside > .ag-vs-item:last-child {
                position: absolute;
                top: 0;
                left: -100%;
            }

            :host::ng-deep > .ag-vs-item {
                position: absolute;
                top: 0;
                left: 0;
                box-shadow: 0 5px 5px rgba(0, 0, 0, 0.1);
                background: #fff;
            }
        `,
    ],
})
export class AgVirtualSrollComponent implements OnInit, AfterViewInit, OnChanges, OnDestroy, AfterContentChecked {
    @ViewChild('itemsContainer', { static: true }) private itemsContainerElRef: ElementRef<HTMLElement>;

    @ContentChildren(AgVsItemComponent) private queryVsItems: QueryList<AgVsItemComponent>;

    @Input('min-row-height') public minRowHeight = 40;
    @Input('height') public height = 'auto';
    @Input('items') public originalItems: any[] = [];

    @Output() private onItemsRender = new EventEmitter<AgVsRenderEvent<any>>();

    public prevOriginalItems: any[] = [];
    public items: any[] = [];

    private subscripAllVsItem: { comp: AgVsItemComponent; subscrip: Subscription }[] = [];

    private _indexCurrentSticky = -1;
    private get indexCurrentSticky() {
        return this._indexCurrentSticky;
    }

    private set indexCurrentSticky(value: number) {
        this._indexCurrentSticky = value;

        const currentIsPrev = value === this.indexPrevSticky;

        if (!currentIsPrev && value >= 0) {
            this.findCurrentStickyByIndex();
        } else {
            if (!currentIsPrev) {
                this.indexNextSticky! = -1;
            }

            if (this.currentStickyItem!) {
                this.currentStickyItem!.comp.isSticked = false;
            }

            this.currentStickyItem = null;
        }

        this.prepareDataItems();
    }

    private get indexPrevSticky() {
        return this.indexesPrevStick.length ? this.indexesPrevStick[0] : -1;
    }

    private set indexPrevSticky(value: number) {
        if (value < 0) {
            if (this.indexesPrevStick.length > 0) {
                this.indexesPrevStick = this.indexesPrevStick.slice(1);
            }
        } else if (!this.indexesPrevStick.some((index) => index === value)) {
            this.indexesPrevStick.push(value);
        }

        if (this.indexesPrevStick.length) {
            this.indexesPrevStick = this.indexesPrevStick.sort((a, b) => b - a);
        }
    }

    private indexNextSticky: any = -1;

    private indexesPrevStick: number[] = [];

    public currentStickyItem: any;

    public currentScroll = 0;
    public contentHeight = 0;
    public paddingTop = 0;

    public startIndex = 0;
    public endIndex = 0;

    private isTable = false;

    private scrollIsUp = false;
    private lastScrollIsUp = false;

    private previousItemsHeight: number[] = [];

    public containerWidth = 0;

    private get itemsNoSticky() {
        return this.currentStickyItem! ? this.items.filter((item) => this.originalItems[this.currentStickyItem!.index] !== item) : this.items;
    }

    public get vsItems() {
        return (this.queryVsItems && this.queryVsItems.toArray()) || [];
    }

    public get numberItemsRendred(): number {
        return this.endIndex - this.startIndex;
    }

    public get el() {
        return this.elRef && this.elRef.nativeElement;
    }

    public get itemsContainerEl() {
        return this.itemsContainerElRef && this.itemsContainerElRef.nativeElement;
    }

    constructor(private elRef: ElementRef<HTMLElement>, private renderer: Renderer2) {}

    ngAfterViewInit() {
        this.queryVsItems.changes.subscribe(() => this.checkStickItem(this.scrollIsUp));
    }

    ngOnInit() {
        this.renderer.listen(this.el, 'scroll', this.onScroll.bind(this));
    }

    ngOnChanges(changes: SimpleChanges) {
        setTimeout(() => {
            if ('height' in changes) {
                this.el.style.height = this.height;
            }

            if ('minRowHeight' in changes) {
                if (typeof this.minRowHeight === 'string') {
                    if (Number(this.minRowHeight)) {
                        this.minRowHeight = Number(this.minRowHeight);
                    } else {
                        console.warn('The [min-row-height] @Input is invalid, the value must be of type "number".');
                        this.minRowHeight = 40;
                    }
                }
            }

            if ('originalItems' in changes) {
                if (!this.originalItems) {
                    this.originalItems = [];
                }

                if (this.currentAndPrevItemsAreDiff()) {
                    this.previousItemsHeight = new Array(this.originalItems.length).fill(null);

                    if (this.el.scrollTop !== 0) {
                        this.el.scrollTop = 0;
                    } else {
                        this.currentScroll = 0;
                        this.prepareDataItems();
                        this.checkIsTable();
                        this.queryVsItems.notifyOnChanges();
                    }
                } else {
                    if (this.originalItems.length > this.prevOriginalItems.length) {
                        this.previousItemsHeight = this.previousItemsHeight.concat(new Array(this.originalItems.length - this.prevOriginalItems.length).fill(null));
                    }

                    this.prepareDataItems();
                    this.checkIsTable();
                    this.queryVsItems.notifyOnChanges();
                }

                this.prevOriginalItems = this.originalItems;
            }
        });
    }

    ngAfterContentChecked() {
        const currentContainerWidth = this.itemsContainerEl && this.itemsContainerEl.clientWidth;
        if (currentContainerWidth !== this.containerWidth) {
            this.containerWidth = currentContainerWidth;
        }

        this.manipuleRenderedItems();
    }

    private currentAndPrevItemsAreDiff() {
        if (this.originalItems.length >= this.prevOriginalItems.length) {
            const begin = 0;
            const end = this.prevOriginalItems.length - 1;
            for (let i = begin; i <= end; i++) {
                if (this.originalItems[i] !== this.prevOriginalItems[i]) {
                    return true;
                }
            }

            return false;
        }

        return true;
    }

    private onScroll() {
        const up = this.el.scrollTop < this.currentScroll;
        this.currentScroll = this.el.scrollTop;

        this.prepareDataItems();
        this.isTable = this.checkIsTable();
        this.lastScrollIsUp = this.scrollIsUp;
        this.scrollIsUp = up;
        //         this.queryVsItems.notifyOnChanges();
    }

    private prepareDataItems() {
        this.registerCurrentItemsHeight();
        this.prepareDataVirtualScroll();
    }

    private registerCurrentItemsHeight() {
        const children = this.getInsideChildrens();
        for (let i = 0; i < children.length; i++) {
            const child = children[i];
            const realIndex = this.startIndex + i;
            this.previousItemsHeight[realIndex] = child.getBoundingClientRect().height;
        }
    }

    private getDimensions() {
        const dimensions = {
            contentHeight: 0,
            paddingTop: 0,
            itemsThatAreGone: 0,
        };

        dimensions.contentHeight = this.originalItems.reduce((prev, curr, i) => {
            const height = this.previousItemsHeight[i];
            return prev + (height ? height : this.minRowHeight);
        }, 0);

        if (this.currentScroll >= this.minRowHeight) {
            let newPaddingTop = 0;
            let itemsThatAreGone = 0;
            let initialScroll = this.currentScroll;

            for (const h of this.previousItemsHeight) {
                const height = h ? h : this.minRowHeight;
                if (initialScroll >= height) {
                    newPaddingTop += height;
                    initialScroll -= height;
                    itemsThatAreGone++;
                } else {
                    break;
                }
            }

            dimensions.paddingTop = newPaddingTop;
            dimensions.itemsThatAreGone = itemsThatAreGone;
        }

        return dimensions;
    }

    private prepareDataVirtualScroll() {
        const dimensions = this.getDimensions();

        this.contentHeight = dimensions.contentHeight;
        this.paddingTop = dimensions.paddingTop;
        this.startIndex = dimensions.itemsThatAreGone;
        this.endIndex = Math.min(this.startIndex + this.numberItemsCanRender(), this.originalItems.length - 1);

        if (this.indexCurrentSticky >= 0 && (this.startIndex > this.indexCurrentSticky || this.endIndex < this.indexCurrentSticky)) {
            if (this.currentStickyItem!) {
                this.currentStickyItem!.outside = true;
            }
            this.items = [...this.originalItems.slice(this.startIndex, Math.min(this.endIndex + 1, this.originalItems.length)), this.originalItems[this.indexCurrentSticky]];
        } else {
            if (this.currentStickyItem!) {
                this.currentStickyItem!.outside = false;
            }
            this.items = this.originalItems.slice(this.startIndex, Math.min(this.endIndex + 1, this.originalItems.length));
        }

        this.onItemsRender.emit(
            new AgVsRenderEvent<any>({
                items: this.itemsNoSticky,
                startIndex: this.startIndex,
                endIndex: this.endIndex,
                length: this.itemsNoSticky.length,
            }),
        );

        this.manipuleRenderedItems();
    }

    private numberItemsCanRender() {
        return Math.floor(this.el.clientHeight / this.minRowHeight) + 2;
    }

    private manipuleRenderedItems() {
        const children = this.getInsideChildrens();
        for (let i = 0; i < children.length; i++) {
            const child = children[i] as HTMLElement;
            if (child.style.display !== 'none') {
                const realIndex = this.startIndex + i;
                child.style.minHeight = `${this.minRowHeight}px`;
                child.style.height = `${this.minRowHeight}px`;

                const className = (realIndex + 1) % 2 === 0 ? 'even' : 'odd';
                const unclassName = className === 'even' ? 'odd' : 'even';

                child.classList.add(`ag-virtual-scroll-${className}`);
                child.classList.remove(`ag-virtual-scroll-${unclassName}`);
            }
        }
    }

    private getInsideChildrens(): HTMLCollection {
        let childrens = this.itemsContainerEl.children;
        if (childrens.length > 0) {
            if (childrens[0].tagName.toUpperCase() === 'TABLE') {
                childrens = childrens[0].children;
                if (childrens.length > 0) {
                    if (childrens[0].tagName.toUpperCase() === 'TBODY') {
                        childrens = childrens[0].children;
                    } else {
                        childrens = childrens[1].children;
                    }
                }
            }
        }

        return childrens;
    }

    private checkIsTable() {
        let childrens = this.itemsContainerEl.children;
        if (childrens.length > 0) {
            if (childrens[0].tagName.toUpperCase() === 'TABLE') {
                childrens = childrens[0].children;
                if (childrens.length > 0) {
                    if (childrens[0].tagName.toUpperCase() === 'THEAD') {
                        const thead = childrens[0] as HTMLElement;
                        thead.style.transform = `translateY(${Math.abs(this.paddingTop - this.currentScroll)}px)`;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private checkStickItem(up: boolean) {
        if (!this.isTable && this.vsItems.length > 0) {
            this.updateVsItems().subscribe(() => {
                if (this.indexCurrentSticky >= 0) {
                    if (!this.currentStickyItem!) {
                        this.findCurrentStickyByIndex(true);
                        return;
                    }

                    if (this.indexNextSticky! === -1) {
                        this.indexNextSticky! = this.getIndexNextSticky(up);
                    }

                    if (this.currentStickIsEnded(up)) {
                        if (!up) {
                            this.indexPrevSticky = this.indexCurrentSticky;
                            this.indexCurrentSticky = this.getIndexCurrentSticky(up);
                            this.indexNextSticky! = this.getIndexNextSticky(up);
                        } else {
                            if (this.indexPrevSticky >= 0) {
                                this.setPrevAsCurrentSticky();
                            } else {
                                this.indexCurrentSticky = this.getIndexCurrentSticky(up);

                                if (this.indexCurrentSticky >= 0) {
                                    this.indexNextSticky! = this.getIndexNextSticky(up);
                                } else {
                                    this.indexNextSticky = null;
                                }
                            }
                        }
                    }
                } else {
                    this.indexCurrentSticky = this.getIndexCurrentSticky(up);
                    this.indexNextSticky! = this.getIndexNextSticky(up);
                }
            });
        } else {
            this.indexCurrentSticky = -1;
            this.indexNextSticky! = -1;
        }
    }

    private findCurrentStickyByIndex(afterPrev = false) {
        let vsIndex = 0;
        const lastVsIndex = this.vsItems.length - 1;
        const diffMaxItemsRender = this.vsItems.length - this.numberItemsCanRender();

        if (diffMaxItemsRender > 0 && !this.vsItems.some((vsItem, vsIndex1) => this.indexCurrentSticky === this.startIndex + vsIndex1)) {
            vsIndex = lastVsIndex;
            const vsItem = this.vsItems[lastVsIndex];
            const index = this.indexCurrentSticky;
            const offsetTop = this.previousItemsHeight.slice(0, index).reduce((prev, curr) => prev + (curr ? curr : this.minRowHeight), 0);
            vsItem.isSticked = true;
            this.currentStickyItem! = new StickyItem({
                comp: vsItem,
                index,
                vsIndex,
                offsetTop,
                height: vsItem.el.offsetHeight,
                outside: true,
            });
        } else {
            for (const vsItem of this.vsItems) {
                const index = this.startIndex + vsIndex;

                if (this.indexCurrentSticky === index) {
                    const offsetTop = this.previousItemsHeight.slice(0, index).reduce((prev, curr) => prev + (curr ? curr : this.minRowHeight), 0);
                    vsItem.isSticked = true;
                    this.currentStickyItem! = new StickyItem({
                        comp: vsItem,
                        index,
                        vsIndex,
                        offsetTop,
                        height: vsItem.el.offsetHeight,
                    });
                    break;
                }

                vsIndex++;
            }
        }

        if (afterPrev && this.currentStickyItem!) {
            const currentHeight = this.currentStickyItem!.height;
            const offsetBottom = this.paddingTop + currentHeight + Math.abs(this.el.scrollTop - this.paddingTop);
            const offsetTopNext =
                this.indexNextSticky! >= 0 ? this.previousItemsHeight.slice(0, this.indexNextSticky!).reduce((prev, curr) => prev + (curr ? curr : this.minRowHeight), 0) : null;

            if (offsetTopNext !== null && offsetBottom >= offsetTopNext) {
                const newDiffTop = offsetBottom - offsetTopNext;
                if (newDiffTop >= currentHeight) {
                    this.currentStickyItem!.diffTop = currentHeight;
                    return true;
                } else {
                    this.currentStickyItem!.diffTop = newDiffTop;
                }
            } else {
                this.currentStickyItem!.diffTop = 0;
            }
        }
    }

    private setPrevAsCurrentSticky() {
        const currentSticked = this.currentStickyItem! && this.currentStickyItem!.comp.sticky;

        if (currentSticked) {
            this.indexNextSticky! = this.indexCurrentSticky;
        }

        this.indexCurrentSticky = this.indexPrevSticky;
        this.indexPrevSticky = -1;
    }

    private getIndexCurrentSticky(up: boolean) {
        let vsIndex = 0;
        for (const vsItem of this.vsItems) {
            const index = vsIndex + this.startIndex;

            const offsetTop = this.previousItemsHeight.slice(0, index).reduce((prev, curr) => prev + (curr ? curr : this.minRowHeight), 0);

            if (vsItem && vsItem.sticky && this.el.scrollTop >= offsetTop && (this.indexCurrentSticky === -1 || index !== this.indexCurrentSticky)) {
                return index;
            }

            vsIndex++;
        }

        return -1;
    }

    private getIndexNextSticky(up: boolean) {
        if (this.indexCurrentSticky >= 0) {
            let vsIndex = 0;

            for (const vsItem of this.vsItems.slice(0, this.numberItemsCanRender())) {
                const index = vsIndex + this.startIndex;

                if (vsItem.sticky && index > this.indexCurrentSticky) {
                    return index;
                }

                vsIndex++;
            }
        }

        return -1;
    }

    private currentStickIsEnded(up: boolean) {
        const currentHeight = this.currentStickyItem!.height;

        if (!up || this.currentStickyItem!.diffTop > 0) {
            const offsetBottom = this.paddingTop + currentHeight + Math.abs(this.el.scrollTop - this.paddingTop);
            const offsetTopNext =
                this.indexNextSticky! >= 0 ? this.previousItemsHeight.slice(0, this.indexNextSticky!).reduce((prev, curr) => prev + (curr ? curr : this.minRowHeight), 0) : null;

            if (offsetTopNext !== null && offsetBottom >= offsetTopNext) {
                const newDiffTop = offsetBottom - offsetTopNext;
                if (newDiffTop >= currentHeight) {
                    this.currentStickyItem!.diffTop = currentHeight;
                    return true;
                } else {
                    this.currentStickyItem!.diffTop = newDiffTop;
                }
            } else {
                this.currentStickyItem!.diffTop = 0;
            }
        } else {
            const offsetBottom = this.paddingTop + Math.abs(this.el.scrollTop - this.paddingTop);
            if (offsetBottom <= this.currentStickyItem!.offsetTop) {
                return true;
            }
        }

        return false;
    }

    private updateVsItems() {
        return new Observable<void>((subscriber) => {
            if (this.subscripAllVsItem.length) {
                this.subscripAllVsItem.forEach((item) => item.subscrip.unsubscribe());
                this.subscripAllVsItem = [];
            }

            const interval = setInterval(() => {
                const diffMaxItemsRender = this.vsItems.length - this.numberItemsCanRender();
                const lastIndex = this.vsItems.length - 1;
                const ok = this.vsItems.every((vsItem, vsIndex) => {
                    let index = this.startIndex + vsIndex;

                    if (diffMaxItemsRender > 0 && vsIndex === lastIndex) {
                        index = this.indexCurrentSticky;
                    }

                    if (!this.currentStickyItem! || vsItem !== this.currentStickyItem!.comp) {
                        vsItem.isSticked = false;
                    }

                    if (!this.subscripAllVsItem.some((item) => item.comp === vsItem)) {
                        this.subscripAllVsItem.push({
                            comp: vsItem,
                            subscrip: vsItem.onStickyChange.subscribe((sticky) => {
                                this.onStickyComponentChanged(vsItem, index);
                            }),
                        });
                    }

                    try {
                        vsItem.forceUpdateInputs();
                    } catch {
                        return false;
                    }

                    return true;
                });

                if (ok) {
                    clearInterval(interval);
                    this.manipuleRenderedItems();
                    subscriber.next();
                }
            });
        });
    }

    private onStickyComponentChanged(vsItem: AgVsItemComponent, index: number) {
        if (index === this.indexCurrentSticky) {
            if (!vsItem.sticky) {
                if (this.indexPrevSticky >= 0) {
                    this.setPrevAsCurrentSticky();
                } else {
                    this.indexCurrentSticky = this.getIndexCurrentSticky(false);

                    if (this.indexCurrentSticky >= 0) {
                        this.indexNextSticky! = this.getIndexNextSticky(false);
                    } else {
                        this.indexNextSticky! = null;
                    }
                }
            }
        } else if ((this.indexCurrentSticky !== -1 && index < this.indexCurrentSticky) || index === this.indexPrevSticky) {
            if (vsItem.sticky) {
                this.indexPrevSticky = index;
            } else {
                this.indexesPrevStick = this.indexesPrevStick.filter((indexPrev) => indexPrev !== index);
            }
        } else if ((this.indexCurrentSticky !== -1 && index > this.indexCurrentSticky) || index === this.indexNextSticky!) {
            if (vsItem.sticky && this.indexNextSticky! !== null && (this.indexNextSticky! === -1 || index < this.indexNextSticky!)) {
                this.indexNextSticky! = index;
            } else if (!vsItem.sticky) {
                this.indexNextSticky! = -1;
            }
        } else {
            return;
        }

        this.queryVsItems.notifyOnChanges();
    }

    ngOnDestroy() {}
}

export class StickyItem {
    comp: AgVsItemComponent;
    index: number;
    offsetTop = 0;
    vsIndex: number;
    diffTop = 0;
    isUp = false;
    height = 0;
    outside = false;

    constructor(obj?: Partial<StickyItem>) {
        if (obj) {
            Object.assign(this, obj);
        }
    }
}
