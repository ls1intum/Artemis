import { Component, HostListener, OnChanges, Signal, SimpleChanges, ViewChild, computed, inject, input, output, signal } from '@angular/core';
import { IconDefinition, faChevronRight, faCog, faEllipsis } from '@fortawesome/free-solid-svg-icons';
import { NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { NgClass, NgTemplateOutlet, SlicePipe } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ImageComponent } from 'app/shared/image/image.component';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { Course } from 'app/core/course/shared/entities/course.model';
import { LayoutService } from 'app/shared/breakpoints/layout.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { CustomBreakpointNames } from 'app/shared/breakpoints/breakpoints.service';

export interface CourseActionItem {
    title: string;
    icon?: IconDefinition;
    translation: string;
    action?: (item?: CourseActionItem) => void;
}

export interface SidebarItem {
    routerLink: string;
    icon?: IconDefinition;
    title: string;
    testId?: string;
    translation: string;
    featureToggle?: FeatureToggle;
    hidden: boolean;
    isPrefix?: boolean;
    bottom?: boolean;
}

@Component({
    selector: 'jhi-course-sidebar',
    templateUrl: './course-sidebar.component.html',
    styleUrls: ['./course-sidebar.component.scss'],
    imports: [
        NgClass,
        NgbDropdown,
        NgbDropdownToggle,
        NgTemplateOutlet,
        NgbDropdownMenu,
        NgbDropdownItem,
        FaIconComponent,
        TranslateDirective,
        NgbTooltip,
        RouterLink,
        ImageComponent,
        RouterLinkActive,
        FeatureToggleHideDirective,
        SlicePipe,
    ],
})
export class CourseSidebarComponent implements OnChanges {
    protected readonly faChevronRight = faChevronRight;
    protected readonly faEllipsis = faEllipsis;
    protected readonly faCog = faCog;

    course = input<Course | undefined>();
    courses = input<Course[] | undefined>();
    sidebarItemsTop = signal<SidebarItem[]>([]);
    sidebarItemsBottom = signal<SidebarItem[]>([]);
    sidebarItems = input<SidebarItem[]>([]);
    courseActionItems = input<CourseActionItem[]>([]);
    isNavbarCollapsed = input<boolean>(false);
    isExamStarted = input<boolean>(false);
    isProduction = input<boolean>(true);
    isTestServer = input<boolean>(false);
    hasUnreadMessages = input<boolean>(false);
    communicationRouteLoaded = input<boolean>(false);
    layoutService = inject(LayoutService);

    hiddenItems = signal<SidebarItem[]>([]);
    anyItemHidden = signal<boolean>(false);

    switchCourse = output<Course>();
    courseActionItemClick = output<CourseActionItem>();
    toggleCollapseState = output<void>();
    activeBreakpoints: Signal<string[]>;
    canExpand: Signal<boolean>;

    @ViewChild('itemsDrop') itemsDrop!: NgbDropdown;

    // Constants for threshold calculation
    readonly WINDOW_OFFSET: number = 225;
    readonly ITEM_HEIGHT: number = 38;

    constructor() {
        this.activeBreakpoints = toSignal(this.layoutService.subscribeToLayoutChanges(), { initialValue: [] as string[] });
        this.canExpand = computed(() => {
            this.activeBreakpoints();
            return this.layoutService.isBreakpointActive(CustomBreakpointNames.sidebarExpandable);
        });
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['sidebarItems']) {
            this.updateVisibleNavbarItems(window.innerHeight);
            this.sidebarItemsTop.set(this.sidebarItems().filter((item) => !item.bottom));
            this.sidebarItemsBottom.set(this.sidebarItems().filter((item) => item.bottom));
        }
    }

    /** Listen window resize event by height */
    @HostListener('window:resize')
    onResize() {
        this.updateVisibleNavbarItems(window.innerHeight);
    }

    /** Update sidebar item's hidden property based on the window height to display three-dots */
    updateVisibleNavbarItems(height: number) {
        const threshold = this.calculateThreshold();
        this.applyThreshold(threshold, height);

        if (!this.anyItemHidden() && this.itemsDrop) {
            this.itemsDrop.close();
        }
    }

    /**  Applies the visibility threshold to sidebar items, determining which items should be hidden.*/
    applyThreshold(threshold: number, height: number) {
        const newHiddenItems: SidebarItem[] = [];
        let newAnyItemHidden = false;
        if (this.sidebarItems()) {
            const reversedSidebarItems = [...this.sidebarItems()].filter((item) => !item.bottom).reverse();

            reversedSidebarItems.forEach((item, index) => {
                const currentThreshold = threshold - index * this.ITEM_HEIGHT;
                item.hidden = height <= currentThreshold;
                if (item.hidden) {
                    newAnyItemHidden = true;
                    newHiddenItems.unshift(item);
                }
            });
        }

        this.anyItemHidden.set(newAnyItemHidden);
        this.hiddenItems.set(newHiddenItems);
    }

    /** Calculate threshold levels based on the number of entries in the sidebar */
    calculateThreshold(): number {
        if (!this.sidebarItems()) {
            return this.WINDOW_OFFSET;
        }
        return this.sidebarItems().length * this.ITEM_HEIGHT + this.WINDOW_OFFSET;
    }
}
