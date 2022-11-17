/**
 * Component based on Open Source Project ag-virtual-scroll
 * https://github.com/ericferreira1992/ag-virtual-scroll
 *
 */

import { Component, ElementRef, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, Renderer2, SimpleChanges, ViewChild } from '@angular/core';
import { NavigationStart, Router } from '@angular/router';
import { VirtualScrollRenderEvent } from 'app/shared/virtual-scroll/virtual-scroll-render-event.class';

@Component({
    selector: 'virtual-scroll',
    templateUrl: './virtual-scroll.component.html',
    styleUrls: ['virtual-scroll.style.css'],
})
export class VirtualScrollComponent implements OnInit, OnChanges, OnDestroy {
    @ViewChild('itemsContainer', { static: true }) private itemsContainerElRef: ElementRef<HTMLElement>;

    @Input('items') public originalItems: any[] = [];
    @Input() forceReload: boolean;
    @Output() forceReloadChange = new EventEmitter<boolean>();
    @Output() private onItemsRender = new EventEmitter<VirtualScrollRenderEvent<any>>();

    minRowHeight = 126.7;

    public prevOriginalItems: any[] = [];
    public items: any[] = [];

    public currentScroll = 0;
    public contentHeight = 0;
    public paddingTop = 0;

    public startIndex = 0;
    public endIndex = 0;

    private scrollIsUp = false;
    private lastScrollIsUp = false;

    private previousItemsHeight: any[] = [];

    scrollListener: any;
    focusInListener: any;

    public get el() {
        return this.elRef.nativeElement;
    }

    public get itemsContainerEl() {
        return this.itemsContainerElRef && this.itemsContainerElRef.nativeElement;
    }

    constructor(private elRef: ElementRef<HTMLElement>, private renderer: Renderer2, private router: Router) {}

    ngOnInit() {
        this.focusInListener = this.renderer.listen(this.el, 'focusin', this.onFocusIn.bind(this));
        this.scrollListener = this.renderer.listen(this.el, 'scroll', this.onScroll.bind(this));
        this.router.events.forEach((event) => {
            if (event instanceof NavigationStart) {
                this.onNavigate();
            }
        });

        this.minRowHeight = Number(this.minRowHeight);
    }

    ngOnChanges(changes: SimpleChanges) {
        setTimeout(() => {
            if ('originalItems' in changes) {
                if (!this.originalItems) {
                    return;
                }

                if (this.forceReload) {
                    this.previousItemsHeight = new Array(this.originalItems.length).fill(null);

                    if (this.el.scrollTop !== 0) {
                        // scroll to the top of the elements
                        this.el.scrollTop = 0;
                    } else {
                        this.currentScroll = 0;
                        this.prepareDataItems();
                    }
                } else {
                    if (this.originalItems.length > this.prevOriginalItems.length) {
                        // next page of arriving elements are appended to the end of the items list and are processed

                        this.previousItemsHeight = this.previousItemsHeight.concat(new Array(this.originalItems.length - this.prevOriginalItems.length).fill(null));
                        this.prepareDataItems();
                    } else {
                        // changes in the displayed elements are reflected to the user
                        for (let i = 0; i < this.prevOriginalItems.length; i++) {
                            const displayIndex = i - this.startIndex;
                            if (displayIndex >= 0 && i <= this.endIndex) {
                                // if item is displayed to the user then it is updated
                                this.items[displayIndex] = this.originalItems[i];
                            }
                        }
                    }
                }
                this.prevOriginalItems = this.originalItems;
                this.forceReloadChange.emit(false);
            }
        });
    }

    /**
     * workaround to prevent automatic scrolling when user clicks the text area of the ace-editor for answerPosts
     *
     */
    private onFocusIn() {
        this.el.scrollTop = this.currentScroll;
    }

    private onScroll() {
        this.lastScrollIsUp = this.scrollIsUp;
        this.scrollIsUp = this.el.scrollTop < this.currentScroll;

        this.currentScroll = this.el.scrollTop;
        this.prepareDataItems();
    }

    /**
     * scroll to the top of posts in case user clicks to a post title to solely display it
     *
     */
    private onNavigate() {
        this.forceReloadChange.emit(true);
    }

    private prepareDataItems() {
        this.registerCurrentItemsHeight();
        this.prepareDataVirtualScroll();
    }

    private registerCurrentItemsHeight(itemsThatAreGone?: number) {
        const children = this.itemsContainerEl.children;
        for (let i = 0; i < children.length; i++) {
            const child = children[i];
            const realIndex = this.startIndex + i;

            if (itemsThatAreGone !== undefined && i === itemsThatAreGone) {
                let answerListHeight = 0;

                child.querySelectorAll('.answer-post').forEach((subElement) => {
                    answerListHeight += subElement.getBoundingClientRect().height;
                });

                if (answerListHeight > 0) {
                    // recalculate element height for posts that are removed from the DOM tree via reducing by their answerPost heights
                    this.previousItemsHeight[realIndex] = child.getBoundingClientRect().height - answerListHeight;
                    this.el.scrollTop -= answerListHeight;
                    this.currentScroll = this.el.scrollTop;
                }
            } else {
                this.previousItemsHeight[realIndex] = child.getBoundingClientRect().height;
            }
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

        if (dimensions.itemsThatAreGone > this.startIndex) {
            // recalculate height for element to be removed
            this.registerCurrentItemsHeight(dimensions.itemsThatAreGone - 1 - this.startIndex);
        }

        this.startIndex = dimensions.itemsThatAreGone;
        this.endIndex = Math.min(this.startIndex + this.numberItemsCanRender(), this.originalItems.length - 1);

        this.items = this.originalItems.slice(this.startIndex, Math.min(this.endIndex + 1, this.originalItems.length));

        // information about currently rendered items are emitted
        this.onItemsRender.emit(
            new VirtualScrollRenderEvent<any>({
                items: this.items,
                startIndex: this.startIndex,
                endIndex: this.endIndex,
                length: this.items.length,
            }),
        );

        this.manipulateRenderedItems();
    }

    private numberItemsCanRender() {
        // total number of items that are displayed
        return Math.floor(this.el.clientHeight / this.minRowHeight) + 2;
    }

    private manipulateRenderedItems() {
        const children = this.itemsContainerEl.children;
        for (let i = 0; i < children.length; i++) {
            const child = children[i] as HTMLElement;
            if (child.style.display !== 'none') {
                child.style.minHeight = `${this.minRowHeight}px`;
                child.style.height = `${this.minRowHeight}px`;
            }
        }
    }

    ngOnDestroy() {
        // stop listening to events
        this.scrollListener();
        this.focusInListener();
    }
}
