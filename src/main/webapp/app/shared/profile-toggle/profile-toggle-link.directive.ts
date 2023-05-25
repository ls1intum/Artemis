import { ChangeDetectorRef, Directive, HostBinding, Input, OnDestroy, OnInit, SkipSelf } from '@angular/core';
import { ProfileToggle, ProfileToggleService } from 'app/shared/profile-toggle/profile-toggle.service';
import { tap } from 'rxjs/operators';
import { Subscription } from 'rxjs';

@Directive({
    selector: '[jhiProfileToggleLink]',
})
export class ProfileToggleLinkDirective implements OnInit, OnDestroy {
    @Input('jhiProfileToggleLink') profile: ProfileToggle;
    private profileActive = true;

    private profileToggleActiveSubscription: Subscription;

    constructor(@SkipSelf() protected cdRef: ChangeDetectorRef, private profileToggleService: ProfileToggleService) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit() {
        // If no profile is set for the toggle, the directive does nothing.
        if (this.profile) {
            this.profileToggleActiveSubscription = this.profileToggleService
                .getProfileToggleActive(this.profile)
                .pipe(
                    // Disable the element if the profile is inactive.
                    tap((active) => {
                        this.profileActive = active;
                        // Required to update OnPush-changes
                        this.cdRef.detectChanges();
                    }),
                )
                .subscribe();
        }
    }

    /**
     * Life cycle hook called by Angular for cleanup just before Angular destroys the component
     */
    ngOnDestroy(): void {
        if (this.profileToggleActiveSubscription) {
            this.profileToggleActiveSubscription.unsubscribe();
        }
    }

    /**
     * This will disable the link if the specified profile is inactive.
     */
    @HostBinding('class.disabled')
    get disabled(): boolean {
        return !this.profileActive;
    }
}
