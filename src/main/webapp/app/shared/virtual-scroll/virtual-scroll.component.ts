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

    constructor(private elRef: ElementRef<HTMLElement>, private renderer: Renderer2, private router: Router) {}

    ngOnInit() {
        this.focusInListener = this.renderer.listen(this.elRef.nativeElement, 'focusin', this.onFocusIn.bind(this));
        this.scrollListener = this.renderer.listen(this.elRef.nativeElement, 'scroll', this.onScroll.bind(this));
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
                    // invalidate previously calculated item heights
                    this.previousItemsHeight = new Array(this.originalItems.length).fill(null);

                    if (this.elRef.nativeElement.scrollTop !== 0) {
                        // scroll to the top of the elements
                        this.elRef.nativeElement.scrollTop = 0;
                    } else {
                        this.currentScroll = 0;
                        this.prepareDataItems();
                    }
                } else {
                    if (this.originalItems.length > this.prevOriginalItems.length) {
                        // previousItemsHeight array is extended for next page of arriving elements
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
     * prevents automatic scrolling to other posts when user clicks the text area of posting markdown editor while creating/editing answerPosts
     */
    private onFocusIn() {
        this.elRef.nativeElement.scrollTop = this.currentScroll;
    }

    /**
     * catches scroll event, calculates scroll direction and saves new distance from the top
     */
    private onScroll() {
        this.lastScrollIsUp = this.scrollIsUp;
        this.scrollIsUp = this.elRef.nativeElement.scrollTop < this.currentScroll;

        this.currentScroll = this.elRef.nativeElement.scrollTop;
        this.prepareDataItems();
    }

    /**
     * scroll to the top of posts in case user clicks to a post title to solely display that post
     */
    private onNavigate() {
        this.forceReloadChange.emit(true);
    }

    private prepareDataItems() {
        this.registerCurrentItemsHeight();
        this.prepareDataVirtualScroll();
    }

    /**
     * updates the stored heights of elements currently available in the DOM tree
     * this update is necessary to correctly realize when to display and remove elements from the DOM tree, with respect to the amount of height the user scrolls
     * @param itemsThatAreGone  real index of element which is to be removed from the DOM Tree
     */
    private registerCurrentItemsHeight(itemsThatAreGone?: number) {
        const children = this.itemsContainerElRef.nativeElement.children;
        for (let i = 0; i < children.length; i++) {
            const child = children[i];
            const realIndex = this.startIndex + i;
            let collapsableHeight = 0;

            /* calculates collapsible nested components of item being removed from the DOM tree and updates the elements height where nested elements would be closed
               this operation is necessary to have a smooth scrolling experience when redisplaying an element previously removed from the DOM tree due to being above the screen's
               upper border */
            if (itemsThatAreGone !== undefined && i === itemsThatAreGone) {
                // height of answerPosts
                child.querySelectorAll('.answer-post').forEach((subElement) => {
                    collapsableHeight += subElement.getBoundingClientRect().height;
                });

                // height of posting markdown editor used to create new answerPosts
                child.querySelectorAll('.new-reply-inline-input').forEach((subElement) => {
                    collapsableHeight += subElement.getBoundingClientRect().height;
                });
            }

            // update height of posts that are removed from the DOM tree
            // reduce height according to the height their answerPosts and posting markdown editor occupies within the user's display
            this.previousItemsHeight[realIndex] = child.getBoundingClientRect().height - collapsableHeight;

            if (collapsableHeight > 0) {
                // scroll upwards by the height of collapsed nested components of the removed post to prevent unintentional automatic scrolling to other posts
                this.elRef.nativeElement.scrollTop -= collapsableHeight;
                // register currentScroll after update
                this.currentScroll = this.elRef.nativeElement.scrollTop;
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

        // update available elements on the DOM tree
        this.items = this.originalItems.slice(this.startIndex, Math.min(this.endIndex + 1, this.originalItems.length));

        // information about the currently rendered items are emitted
        this.onItemsRender.emit(
            new VirtualScrollRenderEvent<any>({
                items: this.items,
                startIndex: this.startIndex,
                endIndex: this.endIndex,
                length: this.items.length,
            }),
        );
    }

    private numberItemsCanRender() {
        // total number of items that can be rendered
        return Math.floor(this.elRef.nativeElement.clientHeight / this.minRowHeight) + 2;
    }

    ngOnDestroy() {
        // stop listening to events
        this.scrollListener();
        this.focusInListener();
    }
}
