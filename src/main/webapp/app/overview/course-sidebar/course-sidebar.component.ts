import { Component, EventEmitter, HostListener, Input, OnChanges, OnInit, Output, SimpleChanges, ViewChild } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { IconDefinition, faChevronRight, faEllipsis } from '@fortawesome/free-solid-svg-icons';
import { NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { NgClass, NgTemplateOutlet, SlicePipe } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { OrionFilterDirective } from 'app/shared/orion/orion-filter.directive';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';

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
    hasInOrionProperty?: boolean;
    showInOrionWindow?: boolean;
    guidedTour?: boolean;
    featureToggle?: FeatureToggle;
    hidden: boolean;
}

@Component({
    selector: 'jhi-course-sidebar',
    templateUrl: './course-sidebar.component.html',
    styleUrls: ['../../overview/course-overview.scss', './course-sidebar.component.scss'],
    standalone: true,
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
        SecuredImageComponent,
        OrionFilterDirective,
        RouterLinkActive,
        FeatureToggleHideDirective,
        SlicePipe,
    ],
})
export class CourseSidebarComponent implements OnInit, OnChanges {
    @Input() course: Course | undefined;
    @Input() courses: Course[] | undefined;
    @Input() sidebarItems: SidebarItem[] = [];
    @Input() courseActionItems: CourseActionItem[] = [];
    @Input() isNavbarCollapsed = false;
    @Input() isExamStarted = false;
    @Input() isProduction = true;
    @Input() isTestServer = false;
    @Input() hasUnreadMessages = false;
    @Input() communicationRouteLoaded = false;

    // Track hidden items internally
    hiddenItems: SidebarItem[] = [];
    anyItemHidden = false;

    @Output() switchCourse = new EventEmitter<Course>();
    @Output() courseActionItemClick = new EventEmitter<CourseActionItem>();
    @Output() toggleCollapseState = new EventEmitter<void>();

    @ViewChild('itemsDrop') itemsDrop: NgbDropdown;

    // Constants for threshold calculation
    readonly WINDOW_OFFSET: number = 300;
    readonly ITEM_HEIGHT: number = 38;

    // Icons
    faChevronRight = faChevronRight;
    faEllipsis = faEllipsis;

    ngOnInit() {
        this.updateVisibleNavbarItems(window.innerHeight);
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.sidebarItems && !changes.sidebarItems.firstChange) {
            this.updateVisibleNavbarItems(window.innerHeight);
        }
    }

    /** Listen window resize event by height */
    @HostListener('window:resize', ['$event'])
    onResize() {
        this.updateVisibleNavbarItems(window.innerHeight);
    }

    /** Update sidebar item's hidden property based on the window height to display three-dots */
    updateVisibleNavbarItems(height: number) {
        const threshold = this.calculateThreshold();
        this.applyThreshold(threshold, height);

        if (!this.anyItemHidden && this.itemsDrop) {
            this.itemsDrop.close();
        }
    }

    /**  Applies the visibility threshold to sidebar items, determining which items should be hidden.*/
    applyThreshold(threshold: number, height: number) {
        this.anyItemHidden = false;
        this.hiddenItems = [];

        // Reverse the sidebar items to remove items starting from the bottom
        const reversedSidebarItems = [...this.sidebarItems].reverse();
        reversedSidebarItems.forEach((item, index) => {
            const currentThreshold = threshold - index * this.ITEM_HEIGHT;
            item.hidden = height <= currentThreshold;
            if (item.hidden) {
                this.anyItemHidden = true;
                this.hiddenItems.unshift(item);
            }
        });
    }

    /** Calculate threshold levels based on the number of entries in the sidebar */
    calculateThreshold(): number {
        return this.sidebarItems.length * this.ITEM_HEIGHT + this.WINDOW_OFFSET;
    }
}
