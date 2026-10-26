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
    selector: 'jhi-user-settings-v2-sidebar',
    templateUrl: './user-settings-v2-sidebar.component.html',
    imports: [FaIconComponent, RouterLink, RouterLinkActive, TumAetUiTooltipDirective, ArtemisTranslatePipe],
    host: { class: 'flex h-full flex-col bg-[var(--module-bg)]' },
})
export class UserSettingsV2SidebarComponent {
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
                routerLink: '/user-settings-v2/profile',
                icon: faUser,
                translation: 'artemisApp.userSettings.navigation.profile',
            },
            {
                routerLink: '/user-settings-v2/credentials',
                icon: faKey,
                translation: 'artemisApp.userSettings.navigation.credentials',
            },
        ];

        if (this.isLearningAvailable()) {
            items.push({
                routerLink: '/user-settings-v2/learning',
                icon: faBookOpen,
                translation: 'artemisApp.userSettings.navigation.learning',
            });
        }

        if (this.isAiExperienceAvailable()) {
            items.push({
                routerLink: '/user-settings-v2/ai-experience',
                icon: faWandMagicSparkles,
                translation: 'artemisApp.userSettings.aiExperience',
            });
        }

        items.push({
            routerLink: '/user-settings-v2/exercises',
            icon: faCode,
            translation: 'artemisApp.userSettings.navigation.exercises',
        });

        return items;
    }
}
