/**
 * Component based on Open Source Project ag-virtual-scroll
 * https://github.com/ericferreira1992/ag-virtual-scroll
 *
 */

import { Component, ElementRef, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, Renderer2, SimpleChanges, ViewChild } from '@angular/core';
import { NavigationStart, Router } from '@angular/router';
import { VirtualScrollRenderEvent } from 'app/shared/virtual-scroll/virtual-scroll-render-event.class';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';

@Component({
    selector: 'virtual-scroll',
    templateUrl: './virtual-scroll.component.html',
    styleUrls: ['virtual-scroll.style.css'],
})
export class VirtualScrollComponent implements OnInit, OnChanges, OnDestroy {
    @ViewChild('itemsContainer', { static: true }) private itemsContainerElRef: ElementRef<HTMLElement>;

    @Input('items') public originalItems: any[] | undefined = [];
    @Input() forceReload: boolean;
    @Output() forceReloadChange = new EventEmitter<boolean>();
    @Output() private onItemsRender = new EventEmitter<VirtualScrollRenderEvent<any>>();

    minRowHeight = 126.7;

    public prevOriginalItems: any[] = [];
    public domTreeItems: any[] = [];

    public currentScroll = 0;
    public contentHeight = 0;
    public paddingTop = 0;

    public startIndex = 0;
    public endIndex = 0;

    private scrollIsUp = false;
    private lastScrollIsUp = false;

    previousItemsHeight: any[] = [];

    scrollListener: any;
    focusInListener: any;

    constructor(private elementRef: ElementRef<HTMLElement>, private renderer: Renderer2, private router: Router) {}

    /**
     * start listening to focusin events on initialization to prevent unintentional scrolling when the user focuses into the text area of posting markdown editor
     * start listening to scroll events on initialization to perform virtual scrolling when the user scrolls through the item container
     * start listening to navigationStart events of router and enable forceReloadChange to scroll to the top of the updated item list
     */
    ngOnInit() {
        this.focusInListener = this.renderer.listen(this.elementRef.nativeElement, 'focusin', this.onFocusIn.bind(this));
        this.scrollListener = this.renderer.listen(this.elementRef.nativeElement, 'scroll', this.onScroll.bind(this));
        this.router.events.forEach((event) => {
            if (event instanceof NavigationStart) {
                // scroll to the top of posts in case user clicks to a post title to solely display that post
                this.forceReloadChange.emit(true);
            }
        });
    }

    ngOnChanges(changes: SimpleChanges) {
        setTimeout(() => {
            if ('originalItems' in changes) {
                // on item change
                if (!this.originalItems) {
                    this.originalItems = [];
                }

                if (this.forceReload || this.currentScroll < this.minRowHeight) {
                    // invalidate previously calculated item heights
                    this.previousItemsHeight = new Array(this.originalItems.length).fill(null);

                    // scroll to the top of the items
                    this.elementRef.nativeElement.scrollTop = 0;
                    this.prepareDataItems();
                } else {
                    if (this.originalItems.length > this.prevOriginalItems.length && this.prevOriginalItems.length % ITEMS_PER_PAGE === 0) {
                        // previousItemsHeight array is extended for next page of arriving items
                        this.previousItemsHeight = this.previousItemsHeight.concat(new Array(this.originalItems.length - this.prevOriginalItems.length).fill(null));
                        this.prepareDataItems();
                    } else {
                        let indexOfFirstDisplayedItem: any;
                        // changes in the displayed items are reflected to the user
                        this.domTreeItems.every((domTreeItem) => {
                            // find the index of the first domTreeItem in the updated list of items
                            indexOfFirstDisplayedItem = this.originalItems!.findIndex((originalItem) => originalItem.id === domTreeItem.id);
                            // if the first domTreeItem no longer exists in the updated list of items, proceed to the next domTreeItem available
                            return indexOfFirstDisplayedItem === -1;
                        });

                        // update items on the domTree
                        for (let k = 0; k < this.domTreeItems.length; k++) {
                            this.domTreeItems[k] = this.originalItems[indexOfFirstDisplayedItem + k];
                        }
                    }
                }
                this.prevOriginalItems = this.originalItems;
                this.forceReloadChange.emit(false);
            }
        });
    }

    /**
     * prevents automatic scrolling to other posts when user clicks the text area of posting markdown editor component while creating/editing answerPosts
     */
    onFocusIn() {
        this.elementRef.nativeElement.scrollTop = this.currentScroll;
    }

    /**
     * catches scroll event, calculates scroll direction and saves new distance from the top
     * calls prepareDataItems() to continue with the virtual scrolling logic
     */
    onScroll() {
        this.lastScrollIsUp = this.scrollIsUp;
        this.scrollIsUp = this.elementRef.nativeElement.scrollTop < this.currentScroll;

        this.currentScroll = this.elementRef.nativeElement.scrollTop;
        this.prepareDataItems();
    }

    /**
     *  prepares for and performs virtual scroll
     */
    prepareDataItems() {
        this.registerCurrentItemsHeight();
        this.prepareDataVirtualScroll();
    }

    /**
     * updates the stored heights of items currently available in the DOM tree
     * this update is necessary to correctly realize when to insert and remove items from the DOM tree, considering the amount of height the user scrolls
     * @param itemsThatAreGone  real index of element which is to be removed from the DOM Tree
     */
    registerCurrentItemsHeight(itemsThatAreGone?: number) {
        const children = this.itemsContainerElRef.nativeElement.children;
        for (let i = 0; i < children.length; i++) {
            const child = children[i];
            const realIndex = this.startIndex + i;
            let collapsableHeight = 0;

            /* calculates collapsible nested components of item being removed from the DOM tree and updates the items height where nested items would be closed
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
            // reduce item height according to the height their collapsable components occupies within the user's display
            this.previousItemsHeight[realIndex] = child.getBoundingClientRect().height - collapsableHeight;

            if (collapsableHeight > 0) {
                // scroll upwards by the height of collapsed nested components of the removed post to prevent unintentional automatic scrolling to other posts
                this.elementRef.nativeElement.scrollTop -= collapsableHeight;
                // register currentScroll after update
                this.currentScroll = this.elementRef.nativeElement.scrollTop;
            }
        }
    }

    /**
     * calculates items to display in the DOM tree by comparing currentScroll to the sum of the heights of consecutive items
     */
    getDimensions() {
        const dimensions = {
            contentHeight: 0,
            paddingTop: 0,
            itemsThatAreGone: 0,
        };

        dimensions.contentHeight = this.originalItems!.reduce((prev, curr, i) => {
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

    /**
     * updates elements of the DOM tree, emits currently rendered items, their real start and end indexes and the total number of elements in the DOM tree
     */
    prepareDataVirtualScroll() {
        const dimensions = this.getDimensions();

        this.contentHeight = dimensions.contentHeight;
        this.paddingTop = dimensions.paddingTop;

        if (dimensions.itemsThatAreGone > this.startIndex) {
            // recalculate height for element to be removed
            this.registerCurrentItemsHeight(dimensions.itemsThatAreGone - 1 - this.startIndex);
        }

        this.startIndex = dimensions.itemsThatAreGone;
        this.endIndex = Math.min(this.startIndex + this.numberItemsCanRender(), this.originalItems!.length - 1);

        // update available items on the DOM tree
        this.domTreeItems = this.originalItems!.slice(this.startIndex, Math.min(this.endIndex + 1, this.originalItems!.length));

        // information about the currently rendered items are emitted
        this.onItemsRender.emit(
            new VirtualScrollRenderEvent<any>({
                items: this.domTreeItems,
                startIndex: this.startIndex,
                endIndex: this.endIndex,
                length: this.domTreeItems.length,
            }),
        );
    }

    /**
     *  @return total number of items that are to be rendered on the DOM tree
     */
    numberItemsCanRender() {
        return Math.floor(this.elementRef.nativeElement.clientHeight / this.minRowHeight) + 2;
    }

    /**
     *  stop listening to events
     */
    ngOnDestroy() {
        this.scrollListener();
        this.focusInListener();
    }
}
