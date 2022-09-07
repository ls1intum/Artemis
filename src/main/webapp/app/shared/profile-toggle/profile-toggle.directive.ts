import { Directive, HostBinding, Input, OnDestroy, OnInit } from '@angular/core';
import { tap } from 'rxjs/operators';
import { Subscription } from 'rxjs';
import { ProfileToggle, ProfileToggleService } from 'app/shared/profile-toggle/profile-toggle.service';

@Directive({
    selector: '[jhiProfileToggle]',
})
export class ProfileToggleDirective implements OnInit, OnDestroy {
    @Input('jhiProfileToggle') profiles: ProfileToggle | ProfileToggle[];
    /**
     * This input must be used to overwrite the disabled state given that the profile toggle is inactive.
     * If the normal [disabled] directive of Angular would be used, the HostBinding in this directive would always enable the element if the profile is active.
     */
    @Input() overwriteDisabled: boolean | null;
    /**
     * Condition to check even before checking for the profile toggle. If true, the profile toggle won't get checked.
     * This can be useful e.g. if you use the same button for different profiles (like our delete button) and only want
     * to check the toggle for programming exercises
     */
    @Input() skipProfileToggle: boolean;
    private profileActive = true;

    private profileToggleActiveSubscription: Subscription;

    constructor(private profileToggleService: ProfileToggleService) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit() {
        // If no profile is set for the toggle, the directive does nothing.
        if (!this.profiles) {
            return;
        }
        const profileArray = Array.isArray(this.profiles) ? this.profiles : [this.profiles];
        if (!profileArray.length) {
            return;
        }
        this.profileToggleActiveSubscription = this.profileToggleService
            .getProfileTogglesActive(profileArray)
            .pipe(
                // Disable the element if any of the profiles is inactive.
                tap((active) => {
                    this.profileActive = this.skipProfileToggle || active;
                }),
            )
            .subscribe();
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
     * This will disable the profile component (normally a button) if the specified profile flag is inactive OR
     * if there is some other condition given as an Input, which takes higher priority (input overwriteDisabled)
     */
    @HostBinding('disabled')
    get disabled(): boolean {
        return this.overwriteDisabled || !this.profileActive;
    }
}
