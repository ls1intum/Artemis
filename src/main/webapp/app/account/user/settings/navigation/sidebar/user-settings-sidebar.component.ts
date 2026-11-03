import { Component, computed, inject, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition, faBookOpen, faChevronRight, faCode, faKey, faUser, faWandMagicSparkles } from '@fortawesome/free-solid-svg-icons';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { LayoutService } from 'app/foundation/breakpoints/layout.service';
import { CustomBreakpointNames } from 'app/foundation/breakpoints/breakpoints.service';
import { toSignal } from '@angular/core/rxjs-interop';

interface UserSettingsNavigationItem {
    routerLink: string;
    icon: IconDefinition;
    translation: string;
}

@Component({
    selector: 'jhi-user-settings-sidebar',
    templateUrl: './user-settings-sidebar.component.html',
    imports: [FaIconComponent, RouterLink, RouterLinkActive, TumAetUiTooltipDirective, ArtemisTranslatePipe],
    host: { class: 'flex h-full flex-col bg-[var(--module-bg)]' },
})
export class UserSettingsSidebarComponent {
    private readonly layoutService = inject(LayoutService);

    protected readonly faChevronRight = faChevronRight;

    private readonly activeBreakpoints = toSignal(this.layoutService.subscribeToLayoutChanges(), { initialValue: [] as string[] });
    readonly isCollapsed = input(false);
    readonly isLearningAvailable = input(false);
    readonly isAiExperienceAvailable = input(false);
    readonly toggleCollapseState = output<void>();
    readonly canExpand = computed(() => this.computeCanExpand());
    readonly navigationItems = computed<UserSettingsNavigationItem[]>(() => this.computeNavigationItems());

    private computeCanExpand(): boolean {
        this.activeBreakpoints();
        return this.layoutService.isBreakpointActive(CustomBreakpointNames.sidebarExpandable);
    }

    private computeNavigationItems(): UserSettingsNavigationItem[] {
        const items: UserSettingsNavigationItem[] = [
            {
                routerLink: '/user-settings/profile',
                icon: faUser,
                translation: 'artemisApp.userSettings.navigation.profile',
            },
            {
                routerLink: '/user-settings/credentials',
                icon: faKey,
                translation: 'artemisApp.userSettings.navigation.credentials',
            },
        ];

        if (this.isLearningAvailable()) {
            items.push({
                routerLink: '/user-settings/learning',
                icon: faBookOpen,
                translation: 'artemisApp.userSettings.navigation.learning',
            });
        }

        if (this.isAiExperienceAvailable()) {
            items.push({
                routerLink: '/user-settings/ai-experience',
                icon: faWandMagicSparkles,
                translation: 'artemisApp.userSettings.aiExperience',
            });
        }

        items.push({
            routerLink: '/user-settings/exercises',
            icon: faCode,
            translation: 'artemisApp.userSettings.navigation.exercises',
        });

        return items;
    }
}
