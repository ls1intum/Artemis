import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterOutlet } from '@angular/router';
import { DataGuard } from 'app/account/user/settings/data-guard.service';
import { learnerProfileAvailable } from 'app/account/user/settings/learner-profile/learner-profile-availability';
import { UserSettingsV2SidebarComponent } from 'app/account/user/settings-v2/user-settings-v2-sidebar/user-settings-v2-sidebar.component';
import { UserSettingsV2TitleBarComponent } from 'app/account/user/settings-v2/user-settings-v2-title-bar/user-settings-v2-title-bar.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

@Component({
    selector: 'jhi-user-settings-v2-container',
    templateUrl: './user-settings-v2-container.component.html',
    styleUrl: './user-settings-v2-container.component.scss',
    imports: [RouterOutlet, UserSettingsV2SidebarComponent, UserSettingsV2TitleBarComponent],
})
export class UserSettingsV2ContainerComponent implements OnInit {
    private readonly dataGuard = inject(DataGuard);
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly profileService = inject(ProfileService);

    readonly isSidebarCollapsed = signal(false);
    readonly isLearningAvailable = signal(false);
    readonly isAiExperienceAvailable = signal(false);
    readonly pageTitle = signal('');

    ngOnInit(): void {
        this.isLearningAvailable.set(learnerProfileAvailable(this.profileService));
        this.isAiExperienceAvailable.set(this.dataGuard.isUsingLLM());
    }

    toggleSidebar(): void {
        this.isSidebarCollapsed.update((collapsed) => !collapsed);
    }

    protected updatePageTitle(): void {
        let activeRoute = this.activatedRoute.snapshot;
        while (activeRoute.firstChild) {
            activeRoute = activeRoute.firstChild;
        }
        const routePageTitle = activeRoute.data.pageTitle;
        this.pageTitle.set(typeof routePageTitle === 'string' ? routePageTitle : '');
    }
}
