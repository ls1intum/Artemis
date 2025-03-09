import { Component, HostListener, OnChanges, OnInit, SimpleChanges, ViewChild, inject, input, output, signal } from '@angular/core';
import { Course, isCommunicationEnabled } from 'app/entities/course.model';
import {
    IconDefinition,
    faChalkboardUser,
    faChartBar,
    faChartColumn,
    faChevronLeft,
    faChevronRight,
    faCircleNotch,
    faClipboard,
    faComments,
    faDoorOpen,
    faEllipsis,
    faEye,
    faFlag,
    faGraduationCap,
    faListAlt,
    faNetworkWired,
    faPersonChalkboard,
    faQuestion,
    faSync,
    faTable,
    faTimes,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { NgClass, NgTemplateOutlet, SlicePipe } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { OrionFilterDirective } from 'app/shared/orion/orion-filter.directive';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { CourseUnenrollmentModalComponent } from 'app/overview/course-unenrollment-modal.component';
import dayjs from 'dayjs/esm';
import { ArtemisServerDateService } from 'app/shared/server-date.service';

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
        SecuredImageComponent,
        OrionFilterDirective,
        RouterLinkActive,
        FeatureToggleHideDirective,
        SlicePipe,
    ],
})
export class CourseSidebarComponent implements OnInit, OnChanges {
    course = input<Course>();
    courses = input<Course[]>();
    isNavbarCollapsed = input<boolean>(false);
    isExamStarted = input<boolean>(false);
    isProduction = input<boolean>(true);
    isTestServer = input<boolean>(false);
    hasUnreadMessages = input<boolean>(false);
    communicationRouteLoaded = input<boolean>(false);
    atlasEnabled = input<boolean>(false);

    hiddenItems = signal<SidebarItem[]>([]);
    anyItemHidden = signal<boolean>(false);
    sidebarItems = signal<SidebarItem[]>([]);
    courseActionItems = signal<CourseActionItem[]>([]);

    canUnenroll = signal<boolean>(false);

    switchCourse = output<Course>();
    courseActionItemClick = output<CourseActionItem>();
    toggleCollapseState = output<void>();

    @ViewChild('itemsDrop') itemsDrop!: NgbDropdown;

    // Constants for threshold calculation
    readonly WINDOW_OFFSET: number = 300;
    readonly ITEM_HEIGHT: number = 38;

    // Icons
    faChevronRight = faChevronRight;
    faEllipsis = faEllipsis;
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;
    faTable = faTable;
    faFlag = faFlag;
    faListAlt = faListAlt;
    faChartBar = faChartBar;
    faClipboard = faClipboard;
    faSync = faSync;
    faCircleNotch = faCircleNotch;
    faChevronLeft = faChevronLeft;
    faQuestion = faQuestion;

    serverDateService = inject(ArtemisServerDateService);
    private modalService = inject(NgbModal);

    ngOnInit() {
        this.updateVisibleNavbarItems(window.innerHeight);
        this.sidebarItems.set(this.getSidebarItems());
        this.courseActionItems.set(this.getCourseActionItems());
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

        if (!this.anyItemHidden() && this.itemsDrop) {
            this.itemsDrop.close();
        }
    }

    /**  Applies the visibility threshold to sidebar items, determining which items should be hidden.*/
    applyThreshold(threshold: number, height: number) {
        const newHiddenItems: SidebarItem[] = [];
        let newAnyItemHidden = false;

        const reversedSidebarItems = [...this.sidebarItems()].reverse();
        reversedSidebarItems.forEach((item, index) => {
            const currentThreshold = threshold - index * this.ITEM_HEIGHT;
            item.hidden = height <= currentThreshold;
            if (item.hidden) {
                newAnyItemHidden = true;
                newHiddenItems.unshift(item);
            }
        });

        this.anyItemHidden.set(newAnyItemHidden);
        this.hiddenItems.set(newHiddenItems);
    }

    /** Calculate threshold levels based on the number of entries in the sidebar */
    calculateThreshold(): number {
        return this.sidebarItems().length * this.ITEM_HEIGHT + this.WINDOW_OFFSET;
    }

    getSidebarItems(): SidebarItem[] {
        const sidebarItems = this.getDefaultItems();
        if (this.course()?.lectures) {
            const lecturesItem: SidebarItem = this.getLecturesItems();
            sidebarItems.splice(-1, 0, lecturesItem);
        }
        if (this.course()?.exams && this.hasVisibleExams()) {
            const examsItem: SidebarItem = this.getExamsItems();
            sidebarItems.unshift(examsItem);
        }
        if (isCommunicationEnabled(this.course())) {
            const communicationsItem: SidebarItem = this.getCommunicationsItems();
            sidebarItems.push(communicationsItem);
        }

        if (this.hasTutorialGroups()) {
            const tutorialGroupsItem: SidebarItem = this.getTutorialGroupsItems();
            sidebarItems.push(tutorialGroupsItem);
        }

        if (this.atlasEnabled() && this.hasCompetencies()) {
            const competenciesItem: SidebarItem = this.getCompetenciesItems();
            sidebarItems.push(competenciesItem);
            if (this.course()?.learningPathsEnabled) {
                const learningPathItem: SidebarItem = this.getLearningPathItems();
                sidebarItems.push(learningPathItem);
            }
        }

        if (this.course()?.faqEnabled) {
            const faqItem: SidebarItem = this.getFaqItem();
            sidebarItems.push(faqItem);
        }

        return sidebarItems;
    }

    /**
     * check if there is at least one exam which should be shown
     */
    hasVisibleExams(): boolean {
        if (this.course()?.exams) {
            for (const exam of this.course()!.exams!) {
                if (exam.visibleDate && dayjs(exam.visibleDate).isBefore(this.serverDateService.now())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if the course has any competencies or prerequisites
     */
    hasCompetencies(): boolean {
        return !!(this.course()?.numberOfCompetencies || this.course()?.numberOfPrerequisites);
    }

    /**
     * Check if the course has a tutorial groups
     */
    hasTutorialGroups(): boolean {
        return !!this.course()?.numberOfTutorialGroups;
    }

    openUnenrollStudentModal() {
        const modalRef = this.modalService.open(CourseUnenrollmentModalComponent, { size: 'xl' });
        modalRef.componentInstance.course = this.course;
    }

    getUnenrollItem() {
        const unenrollItem: CourseActionItem = {
            title: 'Unenroll',
            icon: faDoorOpen,
            translation: 'artemisApp.courseOverview.exerciseList.details.unenrollmentButton',
            action: () => this.openUnenrollStudentModal(),
        };
        return unenrollItem;
    }

    getLecturesItems() {
        const lecturesItem: SidebarItem = {
            routerLink: 'lectures',
            icon: faChalkboardUser,
            title: 'Lectures',
            translation: 'artemisApp.courseOverview.menu.lectures',
            hasInOrionProperty: true,
            showInOrionWindow: false,
            hidden: false,
        };
        return lecturesItem;
    }

    getExamsItems() {
        const examsItem: SidebarItem = {
            routerLink: 'exams',
            icon: faGraduationCap,
            title: 'Exams',
            testId: 'exam-tab',
            translation: 'artemisApp.courseOverview.menu.exams',
            hasInOrionProperty: true,
            showInOrionWindow: false,
            hidden: false,
        };
        return examsItem;
    }

    getCommunicationsItems() {
        const communicationsItem: SidebarItem = {
            routerLink: 'communication',
            icon: faComments,
            title: 'Communication',
            translation: 'artemisApp.courseOverview.menu.communication',
            hasInOrionProperty: true,
            showInOrionWindow: false,
            hidden: false,
        };
        return communicationsItem;
    }

    getTutorialGroupsItems() {
        const tutorialGroupsItem: SidebarItem = {
            routerLink: 'tutorial-groups',
            icon: faPersonChalkboard,
            title: 'Tutorials',
            translation: 'artemisApp.courseOverview.menu.tutorialGroups',
            hasInOrionProperty: true,
            showInOrionWindow: false,
            featureToggle: FeatureToggle.TutorialGroups,
            hidden: false,
        };
        return tutorialGroupsItem;
    }

    getCompetenciesItems() {
        const competenciesItem: SidebarItem = {
            routerLink: 'competencies',
            icon: faFlag,
            title: 'Competencies',
            translation: 'artemisApp.courseOverview.menu.competencies',
            hasInOrionProperty: true,
            showInOrionWindow: false,
            hidden: false,
        };
        return competenciesItem;
    }

    getLearningPathItems() {
        const learningPathItem: SidebarItem = {
            routerLink: 'learning-path',
            icon: faNetworkWired,
            title: 'Learning Path',
            translation: 'artemisApp.courseOverview.menu.learningPath',
            hasInOrionProperty: true,
            showInOrionWindow: false,
            featureToggle: FeatureToggle.LearningPaths,
            hidden: false,
        };
        return learningPathItem;
    }

    getDashboardItems() {
        const dashboardItem: SidebarItem = {
            routerLink: 'dashboard',
            icon: faChartBar,
            title: 'Dashboard',
            translation: 'artemisApp.courseOverview.menu.dashboard',
            hasInOrionProperty: false,
            showInOrionWindow: false,
            featureToggle: FeatureToggle.StudentCourseAnalyticsDashboard,
            hidden: false,
        };
        return dashboardItem;
    }

    getFaqItem() {
        const faqItem: SidebarItem = {
            routerLink: 'faq',
            icon: faQuestion,
            title: 'FAQs',
            translation: 'artemisApp.courseOverview.menu.faq',
            hasInOrionProperty: false,
            showInOrionWindow: false,
            hidden: false,
        };
        return faqItem;
    }

    getDefaultItems() {
        const items = [];
        if (this.course()?.studentCourseAnalyticsDashboardEnabled) {
            const dashboardItem: SidebarItem = this.getDashboardItems();
            items.push(dashboardItem);
        }
        const exercisesItem: SidebarItem = {
            routerLink: 'exercises',
            icon: faListAlt,
            title: 'Exercises',
            translation: 'artemisApp.courseOverview.menu.exercises',
            hidden: false,
        };

        const statisticsItem: SidebarItem = {
            routerLink: 'statistics',
            icon: faChartColumn,
            title: 'Statistics',
            translation: 'artemisApp.courseOverview.menu.statistics',
            hasInOrionProperty: true,
            showInOrionWindow: false,
            guidedTour: true,
            hidden: false,
        };

        return items.concat([exercisesItem, statisticsItem]);
    }
    getCourseActionItems(): CourseActionItem[] {
        const courseActionItems = [];
        this.canUnenroll.set(this.canStudentUnenroll() && !this.course()?.isAtLeastTutor);
        if (this.canUnenroll()) {
            const unenrollItem: CourseActionItem = this.getUnenrollItem();
            courseActionItems.push(unenrollItem);
        }
        return courseActionItems;
    }
    canStudentUnenroll(): boolean {
        return !!this.course()?.unenrollmentEnabled && dayjs().isBefore(this.course()?.unenrollmentEndDate);
    }
}
