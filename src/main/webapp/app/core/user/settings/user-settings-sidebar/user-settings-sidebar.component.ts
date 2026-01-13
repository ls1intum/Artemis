import { Component, computed, inject, input, output } from '@angular/core';
import { IconDefinition, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { faBell, faCode, faFingerprint, faFlask, faGraduationCap, faIdCard, faKey, faLock, faRobot, faUser } from '@fortawesome/free-solid-svg-icons';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { LayoutService } from 'app/shared/breakpoints/layout.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { CustomBreakpointNames } from 'app/shared/breakpoints/breakpoints.service';
import { User } from 'app/core/user/user.model';
import { addPublicFilePrefix } from 'app/app.constants';

export interface UserSettingsSidebarItem {
    routerLink: string;
    icon: IconDefinition;
    title: string;
    translation: string;
    testId?: string;
}

export interface UserSettingsSidebarGroup {
    translation: string;
    items: UserSettingsSidebarItem[];
}

@Component({
    selector: 'jhi-user-settings-sidebar',
    templateUrl: './user-settings-sidebar.component.html',
    styleUrls: ['./user-settings-sidebar.component.scss'],
    imports: [NgClass, FaIconComponent, TranslateDirective, ArtemisTranslatePipe, NgbTooltip, RouterLink, RouterLinkActive],
})
export class UserSettingsSidebarComponent {
    protected readonly faChevronRight = faChevronRight;
    protected readonly faUser = faUser;
    protected readonly addPublicFilePrefix = addPublicFilePrefix;

    isNavbarCollapsed = input<boolean>(false);
    isPasskeyEnabled = input<boolean>(false);
    isAtLeastTutor = input<boolean>(false);
    isUsingExternalLLM = input<boolean>(false);
    currentUser = input<User | undefined>(undefined);

    toggleCollapseState = output<void>();

    layoutService = inject(LayoutService);
    activeBreakpoints = toSignal(this.layoutService.subscribeToLayoutChanges(), { initialValue: [] as string[] });
    canExpand = computed(() => {
        this.activeBreakpoints();
        return this.layoutService.isBreakpointActive(CustomBreakpointNames.sidebarExpandable);
    });

    sidebarGroups = computed<UserSettingsSidebarGroup[]>(() => {
        const groups: UserSettingsSidebarGroup[] = [];

        // Group 1: Profile & Account
        groups.push({
            translation: 'artemisApp.userSettings.groups.profileAndAccount',
            items: [
                {
                    routerLink: '/user-settings/account',
                    icon: faUser,
                    title: 'Account Information',
                    translation: 'artemisApp.userSettings.accountInformation',
                    testId: 'user-settings-account',
                },
                {
                    routerLink: '/user-settings/profile',
                    icon: faIdCard,
                    title: 'Learner Profile',
                    translation: 'artemisApp.userSettings.learnerProfile',
                    testId: 'user-settings-profile',
                },
            ],
        });

        // Group 2: Security
        const securityItems: UserSettingsSidebarItem[] = [
            {
                routerLink: '/user-settings/ssh',
                icon: faKey,
                title: 'SSH Keys',
                translation: 'artemisApp.userSettings.sshSettings',
                testId: 'user-settings-ssh',
            },
        ];

        if (this.isAtLeastTutor()) {
            securityItems.push({
                routerLink: '/user-settings/vcs-token',
                icon: faLock,
                title: 'VCS Token',
                translation: 'artemisApp.userSettings.vcsAccessTokenSettings',
                testId: 'user-settings-vcs-token',
            });
        }

        if (this.isPasskeyEnabled()) {
            securityItems.push({
                routerLink: '/user-settings/passkeys',
                icon: faFingerprint,
                title: 'Passkeys',
                translation: 'artemisApp.userSettings.passkeys',
                testId: 'user-settings-passkeys',
            });
        }

        groups.push({
            translation: 'artemisApp.userSettings.groups.security',
            items: securityItems,
        });

        // Group 3: Preferences
        groups.push({
            translation: 'artemisApp.userSettings.groups.preferences',
            items: [
                {
                    routerLink: '/user-settings/ide-preferences',
                    icon: faCode,
                    title: 'IDE Preferences',
                    translation: 'artemisApp.userSettings.categories.IDE_PREFERENCES',
                    testId: 'user-settings-ide',
                },
                {
                    routerLink: '/user-settings/notifications',
                    icon: faBell,
                    title: 'Notifications',
                    translation: 'artemisApp.userSettings.categories.GLOBAL_NOTIFICATIONS',
                    testId: 'user-settings-notifications',
                },
                {
                    routerLink: '/user-settings/quiz-training',
                    icon: faGraduationCap,
                    title: 'Quiz Training',
                    translation: 'artemisApp.userSettings.quizTrainingSettings.quizTraining',
                    testId: 'user-settings-quiz-training',
                },
            ],
        });

        // Group 4: Privacy & Data
        const privacyItems: UserSettingsSidebarItem[] = [];

        if (this.isUsingExternalLLM()) {
            privacyItems.push({
                routerLink: '/user-settings/external-data',
                icon: faRobot,
                title: 'External LLM Usage',
                translation: 'artemisApp.userSettings.externalLLMUsage',
                testId: 'user-settings-external-llm',
            });
        }

        privacyItems.push({
            routerLink: '/user-settings/science',
            icon: faFlask,
            title: 'Science Settings',
            translation: 'artemisApp.userSettings.scienceSettings',
            testId: 'user-settings-science',
        });

        groups.push({
            translation: 'artemisApp.userSettings.groups.privacy',
            items: privacyItems,
        });

        return groups;
    });
}
