import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { NgClass } from '@angular/common';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { Subscription, filter } from 'rxjs';

import { AdminSidebarComponent } from 'app/core/admin/admin-sidebar/admin-sidebar.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS, MODULE_FEATURE_EXAM, PROFILE_LOCALCI, PROFILE_LTI } from 'app/app.constants';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { LayoutService } from 'app/shared/breakpoints/layout.service';
import { CustomBreakpointNames } from 'app/shared/breakpoints/breakpoints.service';

/**
 * Container component for the admin section.
 * Manages the sidebar layout and feature flags for admin functionality.
 */
@Component({
    selector: 'jhi-admin-container',
    templateUrl: './admin-container.component.html',
    styleUrls: ['./admin-container.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NgClass, MatSidenavContainer, MatSidenavContent, MatSidenav, RouterOutlet, AdminSidebarComponent],
    host: {
        '(window:resize)': 'onResize()',
        '(document:keydown.control.m)': 'onKeyDown($event)',
    },
})
export class AdminContainerComponent implements OnInit, OnDestroy {
    private readonly profileService = inject(ProfileService);
    private readonly featureToggleService = inject(FeatureToggleService);
    private readonly layoutService = inject(LayoutService);
    private readonly router = inject(Router);

    /** Whether the navbar is collapsed */
    readonly isNavbarCollapsed = signal(false);

    /** Feature flags */
    readonly localCIActive = signal(false);
    readonly ltiEnabled = signal(false);
    readonly atlasEnabled = signal(false);
    readonly examEnabled = signal(false);
    readonly standardizedCompetenciesEnabled = signal(false);

    private standardizedCompetencySubscription?: Subscription;
    private routerSubscription?: Subscription;

    ngOnInit() {
        const profileInfo = this.profileService.getProfileInfo();
        this.atlasEnabled.set(profileInfo.activeModuleFeatures.includes(MODULE_FEATURE_ATLAS));
        this.examEnabled.set(profileInfo.activeModuleFeatures.includes(MODULE_FEATURE_EXAM));
        this.localCIActive.set(profileInfo.activeProfiles.includes(PROFILE_LOCALCI));
        this.ltiEnabled.set(profileInfo.activeProfiles.includes(PROFILE_LTI));

        this.standardizedCompetencySubscription = this.featureToggleService.getFeatureToggleActive(FeatureToggle.StandardizedCompetencies).subscribe((isActive) => {
            this.standardizedCompetenciesEnabled.set(isActive);
        });

        // Check initial collapse state based on breakpoint
        this.updateCollapseState();

        // Subscribe to router events to handle navigation
        this.routerSubscription = this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => {
            // Could add additional logic here if needed
        });
    }

    ngOnDestroy() {
        this.standardizedCompetencySubscription?.unsubscribe();
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
