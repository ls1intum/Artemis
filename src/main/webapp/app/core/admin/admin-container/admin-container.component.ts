import { Component, HostListener, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { NgClass } from '@angular/common';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { Subscription, filter } from 'rxjs';

import { AdminSidebarComponent } from 'app/core/admin/admin-sidebar/admin-sidebar.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS, MODULE_FEATURE_EXAM, MODULE_FEATURE_PASSKEY, MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN, PROFILE_LOCALCI, PROFILE_LTI } from 'app/app.constants';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { LayoutService } from 'app/shared/breakpoints/layout.service';
import { CustomBreakpointNames } from 'app/shared/breakpoints/breakpoints.service';
import { AccountService } from 'app/core/auth/account.service';
import { IS_AT_LEAST_SUPER_ADMIN } from 'app/shared/constants/authority.constants';

@Component({
    selector: 'jhi-admin-container',
    templateUrl: './admin-container.component.html',
    styleUrls: ['./admin-container.component.scss'],
    imports: [NgClass, MatSidenavContainer, MatSidenavContent, MatSidenav, RouterOutlet, AdminSidebarComponent],
})
export class AdminContainerComponent implements OnInit, OnDestroy {
    private readonly profileService = inject(ProfileService);
    private readonly featureToggleService = inject(FeatureToggleService);
    private readonly layoutService = inject(LayoutService);
    private readonly router = inject(Router);
    private readonly accountService = inject(AccountService);

    isNavbarCollapsed = signal<boolean>(false);

    // Feature flags
    localCIActive = false;
    ltiEnabled = false;
    atlasEnabled = false;
    examEnabled = false;
    standardizedCompetenciesEnabled = false;
    passkeyEnabled = false;
    passkeyRequiredForAdmin = false;
    isSuperAdmin = signal<boolean>(false);

    private standardizedCompetencySubscription?: Subscription;
    private routerSubscription?: Subscription;

    ngOnInit() {
        const profileInfo = this.profileService.getProfileInfo();
        this.atlasEnabled = profileInfo.activeModuleFeatures.includes(MODULE_FEATURE_ATLAS);
        this.examEnabled = profileInfo.activeModuleFeatures.includes(MODULE_FEATURE_EXAM);
        this.localCIActive = profileInfo.activeProfiles.includes(PROFILE_LOCALCI);
        this.ltiEnabled = profileInfo.activeProfiles.includes(PROFILE_LTI);
        this.passkeyEnabled = profileInfo.activeModuleFeatures.includes(MODULE_FEATURE_PASSKEY);
        this.passkeyRequiredForAdmin = profileInfo.activeModuleFeatures.includes(MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN);
        this.isSuperAdmin.set(this.accountService.hasAnyAuthorityDirect(IS_AT_LEAST_SUPER_ADMIN));

        this.standardizedCompetencySubscription = this.featureToggleService.getFeatureToggleActive(FeatureToggle.StandardizedCompetencies).subscribe((isActive) => {
            this.standardizedCompetenciesEnabled = isActive;
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

    @HostListener('window:resize')
    onResize() {
        this.updateCollapseState();
    }

    @HostListener('document:keydown.control.m', ['$event'])
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
