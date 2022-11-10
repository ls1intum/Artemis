import { BehaviorSubject, of } from 'rxjs';
import { distinctUntilChanged, map } from 'rxjs/operators';
import { ActiveProfileToggles, ProfileToggle } from 'app/shared/profile-toggle/profile-toggle.service';

export class MockProfileToggleService {
    private subject: BehaviorSubject<ActiveProfileToggles>;

    subscribeProfileToggleUpdates() {}

    unsubscribeProfileToggleUpdates() {}

    getProfileToggles() {
        const defaultActiveProfileState: ActiveProfileToggles = Object.values(ProfileToggle);
        this.subject = new BehaviorSubject<ActiveProfileToggles>(defaultActiveProfileState);

        return this.subject;
    }

    getProfileToggleActive(profile: ProfileToggle) {
        return this.subject.asObservable().pipe(
            map((activeProfiles) => activeProfiles.includes(profile)),
            distinctUntilChanged(),
        );
    }

    setProfileToggleState(profileToggle: ProfileToggle, active: boolean) {
        const activeProfileToggles = Object.values(ProfileToggle);

        if (!active) {
            const index = activeProfileToggles.indexOf(profileToggle);
            if (index > -1) {
                activeProfileToggles.splice(index, 1);
            }
        }

        return of(this.subject.next(activeProfileToggles));
    }
}
