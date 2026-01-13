import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { NgClass } from '@angular/common';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { Subscription, filter, tap } from 'rxjs';

import { UserSettingsSidebarComponent } from 'app/core/user/settings/user-settings-sidebar/user-settings-sidebar.component';
import { UserSettingsTitleBarComponent } from 'app/core/user/settings/shared/user-settings-title-bar/user-settings-title-bar.component';
import { MODULE_FEATURE_PASSKEY } from 'app/app.constants';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ExternalDataGuard } from 'app/core/user/settings/external-data.guard';
import { LayoutService } from 'app/shared/breakpoints/layout.service';
import { CustomBreakpointNames } from 'app/shared/breakpoints/breakpoints.service';

/**
 * UserSettingsContainerComponent serves as the common ground for different settings.
 * Manages the sidebar layout and feature flags for user settings functionality.
 */
@Component({
    selector: 'jhi-user-settings',
    templateUrl: 'user-settings-container.component.html',
    styleUrls: ['user-settings-container.component.scss'],
    imports: [NgClass, MatSidenavContainer, MatSidenavContent, MatSidenav, RouterOutlet, UserSettingsSidebarComponent, UserSettingsTitleBarComponent],
    host: {
        '(window:resize)': 'onResize()',
        '(document:keydown.control.m)': 'onKeyDown($event)',
    },
})
export class UserSettingsContainerComponent implements OnInit, OnDestroy {
    private readonly profileService = inject(ProfileService);
    private readonly accountService = inject(AccountService);
    private readonly externalDataGuard = inject(ExternalDataGuard);
    private readonly layoutService = inject(LayoutService);
    private readonly router = inject(Router);

    /** Whether the navbar is collapsed */
    readonly isNavbarCollapsed = signal(false);

    /** Feature flags */
    readonly isPasskeyEnabled = signal(false);
    readonly isAtLeastTutor = signal(false);
    readonly isUsingExternalLLM = signal(false);

    currentUser?: User;

    private routerSubscription?: Subscription;

    ngOnInit() {
        this.isPasskeyEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_PASSKEY));
        this.isUsingExternalLLM.set(this.externalDataGuard.isUsingExternalLLM());

        this.accountService
            .getAuthenticationState()
            .pipe(
                tap((user: User) => {
                    this.currentUser = user;
                    this.isAtLeastTutor.set(this.accountService.isAtLeastTutor());
                }),
            )
            .subscribe();

        // Check initial collapse state based on breakpoint
        this.updateCollapseState();

        // Subscribe to router events to handle navigation
        this.routerSubscription = this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => {
            // Could add additional logic here if needed
        });
    }

    ngOnDestroy() {
        this.routerSubscription?.unsubscribe();
    }

    onResize() {
        this.updateCollapseState();
    }

    onKeyDown(event: Event) {
        if (this.layoutService.isBreakpointActive(CustomBreakpointNames.sidebarExpandable)) {
            event.preventDefault();
            this.toggleCollapseState();
        }
    }

    toggleCollapseState() {
        this.isNavbarCollapsed.set(!this.isNavbarCollapsed());
    }

    private updateCollapseState() {
        // Auto-collapse on smaller screens
        if (!this.layoutService.isBreakpointActive(CustomBreakpointNames.sidebarExpandable)) {
            this.isNavbarCollapsed.set(true);
        }
    }
}
