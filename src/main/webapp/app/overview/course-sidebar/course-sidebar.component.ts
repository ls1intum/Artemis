import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { IconDefinition, faChevronRight, faEllipsis } from '@fortawesome/free-solid-svg-icons';
import { NgbDropdown, NgbDropdownButtonItem, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { NgClass, NgTemplateOutlet, SlicePipe } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from '../../shared/language/translate.directive';
import { SecuredImageComponent } from '../../shared/image/secured-image.component';
import { OrionFilterDirective } from '../../shared/orion/orion-filter.directive';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { FeatureToggleHideDirective } from '../../shared/feature-toggle/feature-toggle-hide.directive';

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
    styleUrls: ['../course-overview.scss', '../course-overview.component.scss'],
    standalone: true,
    imports: [
        NgClass,
        NgbDropdown,
        NgbDropdownToggle,
        NgTemplateOutlet,
        NgbDropdownMenu,
        NgbDropdownButtonItem,
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
export class CourseSidebarComponent {
    @Input() course: Course | undefined;
    @Input() courses: Course[] | undefined;
    @Input() sidebarItems: SidebarItem[] = [];
    @Input() courseActionItems: CourseActionItem[] = [];
    @Input() hiddenItems: SidebarItem[] = [];
    @Input() anyItemHidden = false;
    @Input() isNavbarCollapsed = false;
    @Input() isExamStarted = false;
    @Input() isProduction = true;
    @Input() isTestServer = false;
    @Input() hasUnreadMessages = false;
    @Input() communicationRouteLoaded = false;

    @Output() switchCourse = new EventEmitter<Course>();
    @Output() courseActionItemClick = new EventEmitter<CourseActionItem>();
    @Output() toggleCollapseState = new EventEmitter<void>();

    @ViewChild('itemsDrop') itemsDrop: NgbDropdown;

    // Icons
    faChevronRight = faChevronRight;
    faEllipsis = faEllipsis;
}
