import { Component, computed, inject, input, output } from '@angular/core';
import { IconDefinition, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import {
    faBell,
    faBookOpen,
    faBroom,
    faClipboardList,
    faEye,
    faFlag,
    faGears,
    faHeart,
    faList,
    faLock,
    faPlug,
    faPuzzlePiece,
    faStamp,
    faTachometerAlt,
    faTasks,
    faThLarge,
    faToggleOn,
    faUniversity,
    faUser,
    faUserShield,
} from '@fortawesome/free-solid-svg-icons';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { LayoutService } from 'app/shared/breakpoints/layout.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { CustomBreakpointNames } from 'app/shared/breakpoints/breakpoints.service';

export interface AdminSidebarItem {
    routerLink: string;
    icon: IconDefinition;
    title: string;
    translation: string;
    testId?: string;
}

export interface AdminSidebarGroup {
    translation: string;
    items: AdminSidebarItem[];
}

@Component({
    selector: 'jhi-admin-sidebar',
    templateUrl: './admin-sidebar.component.html',
    styleUrls: ['./admin-sidebar.component.scss'],
    imports: [NgClass, FaIconComponent, TranslateDirective, ArtemisTranslatePipe, NgbTooltip, RouterLink, RouterLinkActive],
})
export class AdminSidebarComponent {
    protected readonly faChevronRight = faChevronRight;
    protected readonly faUserShield = faUserShield;

    isNavbarCollapsed = input<boolean>(false);
    localCIActive = input<boolean>(false);
    ltiEnabled = input<boolean>(false);
    standardizedCompetenciesEnabled = input<boolean>(false);
    atlasEnabled = input<boolean>(false);
    examEnabled = input<boolean>(false);

    toggleCollapseState = output<void>();

    layoutService = inject(LayoutService);
    activeBreakpoints = toSignal(this.layoutService.subscribeToLayoutChanges(), { initialValue: [] as string[] });
    canExpand = computed(() => {
        this.activeBreakpoints();
        return this.layoutService.isBreakpointActive(CustomBreakpointNames.sidebarExpandable);
    });

    sidebarGroups = computed<AdminSidebarGroup[]>(() => {
        const groups: AdminSidebarGroup[] = [];

        // Group 1: User & Organization Management
        groups.push({
            translation: 'global.menu.admin.groups.usersAndOrganizations',
            items: [
                {
                    routerLink: '/admin/organization-management',
                    icon: faUniversity,
                    title: 'Organizations',
                    translation: 'global.menu.admin.sidebar.organizations',
                    testId: 'admin-organization-management',
                },
                {
                    routerLink: '/admin/user-management',
                    icon: faUser,
                    title: 'Users',
                    translation: 'global.menu.admin.sidebar.users',
                    testId: 'admin-user-management',
                },
            ],
        });

        // Group 2: Content & Learning
        const contentItems: AdminSidebarItem[] = [];
        if (this.ltiEnabled()) {
            contentItems.push({
                routerLink: '/admin/lti-configuration',
                icon: faPuzzlePiece,
                title: 'LTI',
                translation: 'global.menu.admin.sidebar.lti',
                testId: 'admin-lti-configuration',
            });
        }
        if (this.standardizedCompetenciesEnabled() && this.atlasEnabled()) {
            contentItems.push({
                routerLink: '/admin/standardized-competencies',
                icon: faFlag,
                title: 'Competencies',
                translation: 'global.menu.admin.sidebar.competencies',
                testId: 'admin-standardized-competencies',
            });
        }
        contentItems.push({
            routerLink: '/admin/course-requests',
            icon: faClipboardList,
            title: 'Course Requests',
            translation: 'global.menu.admin.sidebar.courseRequests',
            testId: 'admin-course-requests',
        });
        contentItems.push({
            routerLink: '/admin/upcoming-exams-and-exercises',
            icon: faBookOpen,
            title: 'Upcoming',
            translation: 'global.menu.admin.sidebar.upcoming',
            testId: 'admin-upcoming-exams-and-exercises',
        });

        // Sort alphabetically by title
        contentItems.sort((a, b) => a.title.localeCompare(b.title));

        groups.push({
            translation: 'global.menu.admin.groups.contentAndLearning',
            items: contentItems,
        });

        // Group 3: Monitoring & Diagnostics
        groups.push({
            translation: 'global.menu.admin.groups.monitoringAndDiagnostics',
            items: [
                {
                    routerLink: '/admin/audits',
                    icon: faBell,
                    title: 'Audits',
                    translation: 'global.menu.admin.sidebar.audits',
                    testId: 'admin-audits',
                },
                {
                    routerLink: '/admin/configuration',
                    icon: faList,
                    title: 'Configuration',
                    translation: 'global.menu.admin.sidebar.configuration',
                    testId: 'admin-configuration',
                },
                {
                    routerLink: '/admin/health',
                    icon: faHeart,
                    title: 'Health',
                    translation: 'global.menu.admin.sidebar.health',
                    testId: 'admin-health',
                },
                {
                    routerLink: '/admin/logs',
                    icon: faTasks,
                    title: 'Logs',
                    translation: 'global.menu.admin.sidebar.logs',
                    testId: 'admin-logs',
                },
                {
                    routerLink: '/admin/metrics',
                    icon: faTachometerAlt,
                    title: 'Metrics',
                    translation: 'global.menu.admin.sidebar.metrics',
                    testId: 'admin-metrics',
                },
                {
                    routerLink: '/admin/user-statistics',
                    icon: faEye,
                    title: 'Statistics',
                    translation: 'global.menu.admin.sidebar.statistics',
                    testId: 'admin-statistics',
                },
                {
                    routerLink: '/admin/websocket',
                    icon: faPlug,
                    title: 'Websocket',
                    translation: 'global.menu.admin.sidebar.websocket',
                    testId: 'admin-websocket',
                },
            ],
        });

        // Group 4: Build System (conditional)
        if (this.localCIActive()) {
            groups.push({
                translation: 'global.menu.admin.groups.buildSystem',
                items: [
                    {
                        routerLink: '/admin/build-agents',
                        icon: faGears,
                        title: 'Build Agents',
                        translation: 'global.menu.admin.sidebar.buildAgents',
                        testId: 'admin-build-agents',
                    },
                    {
                        routerLink: '/admin/build-queue',
                        icon: faList,
                        title: 'Build Overview',
                        translation: 'global.menu.admin.sidebar.buildQueue',
                        testId: 'admin-build-queue',
                    },
                ],
            });
        }

        // Group 5: System Configuration
        const systemConfigItems: AdminSidebarItem[] = [
            {
                routerLink: '/admin/cleanup-service',
                icon: faBroom,
                title: 'Cleanup',
                translation: 'global.menu.admin.sidebar.cleanup',
                testId: 'admin-cleanup-service',
            },
            {
                routerLink: '/admin/feature-toggles',
                icon: faToggleOn,
                title: 'Features',
                translation: 'global.menu.admin.sidebar.features',
                testId: 'admin-feature-toggles',
            },
            {
                routerLink: '/admin/imprint',
                icon: faStamp,
                title: 'Imprint',
                translation: 'global.menu.admin.sidebar.imprint',
                testId: 'admin-imprint',
            },
            {
                routerLink: '/admin/privacy-statement',
                icon: faLock,
                title: 'Privacy',
                translation: 'global.menu.admin.sidebar.privacy',
                testId: 'admin-privacy-statement',
            },
            {
                routerLink: '/admin/system-notification-management',
                icon: faBell,
                title: 'Notifications',
                translation: 'global.menu.admin.sidebar.notifications',
                testId: 'admin-system-notifications',
            },
        ];

        if (this.examEnabled()) {
            systemConfigItems.push({
                routerLink: '/admin/exam-rooms',
                icon: faThLarge,
                title: 'Exam Rooms',
                translation: 'global.menu.admin.sidebar.examRooms',
                testId: 'admin-exam-rooms',
            });
        }

        // Sort alphabetically by title
        systemConfigItems.sort((a, b) => a.title.localeCompare(b.title));

        groups.push({
            translation: 'global.menu.admin.groups.systemConfiguration',
            items: systemConfigItems,
        });

        return groups;
    });
}
