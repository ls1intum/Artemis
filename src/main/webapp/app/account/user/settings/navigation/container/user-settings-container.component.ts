import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterOutlet } from '@angular/router';
import { DataGuard } from 'app/account/user/settings/ai-experience/data-guard.service';
import { learnerProfileAvailable } from 'app/account/user/settings/learning/learner-profile-availability';
import { UserSettingsSidebarComponent } from 'app/account/user/settings/navigation/sidebar/user-settings-sidebar.component';
import { UserSettingsTitleBarComponent } from 'app/account/user/settings/navigation/title-bar/user-settings-title-bar.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { getCurrentRouteSignal } from 'app/foundation/route/getCurrentRouteSignal';

@Component({
    selector: 'jhi-user-settings-container',
    templateUrl: './user-settings-container.component.html',
    styleUrl: './user-settings-container.component.scss',
    imports: [RouterOutlet, UserSettingsSidebarComponent, UserSettingsTitleBarComponent],
})
export class UserSettingsContainerComponent implements OnInit {
    private readonly dataGuard = inject(DataGuard);
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly profileService = inject(ProfileService);
    private readonly router = inject(Router);
    private readonly currentRoute = getCurrentRouteSignal(this.router);

    readonly isSidebarCollapsed = signal(false);
    readonly isLearningAvailable = signal(false);
    readonly isAiExperienceAvailable = signal(false);
    readonly pageTitle = computed(() => {
        this.currentRoute();
        let activeRoute = this.activatedRoute.snapshot;
        while (activeRoute.firstChild) {
            activeRoute = activeRoute.firstChild;
        }
        const routePageTitle = activeRoute.data.pageTitle;
        return typeof routePageTitle === 'string' ? routePageTitle : 'artemisApp.userSettings.navigation.profile';
    });

    ngOnInit(): void {
        this.isLearningAvailable.set(learnerProfileAvailable(this.profileService));
        this.isAiExperienceAvailable.set(this.dataGuard.isUsingLLM());
    }

    toggleSidebar(): void {
        this.isSidebarCollapsed.update((collapsed) => !collapsed);
    }
}
