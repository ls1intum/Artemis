import { Directive, HostBinding, Input, OnDestroy, OnInit, TemplateRef, ViewContainerRef } from '@angular/core';
import { ProfileToggle, ProfileToggleService } from 'app/shared/profile-toggle/profile-toggle.service';
import { tap } from 'rxjs/operators';
import { Subscription } from 'rxjs';

@Directive({
    selector: '[jhiProfileToggleHide]',
})
export class ProfileToggleHideDirective implements OnInit, OnDestroy {
    @Input('jhiProfileToggleHide') profile: ProfileToggle;

    private profileActive = true;

    private profileToggleActiveSubscription: Subscription;

    constructor(private profileToggleService: ProfileToggleService) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit() {
        // If no profile is set for the toggle, the directive does nothing.
        if (this.profile) {
            this.profileToggleActiveSubscription = this.profileToggleService
                .getProfileToggleActive(this.profile)
                .pipe(
                    tap((active) => {
                        this.profileActive = active;
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
     * This will hide the element if the profile is inactive.
     */
    @HostBinding('class.d-none')
    get hidden(): boolean {
        return !this.profileActive;
    }
}
