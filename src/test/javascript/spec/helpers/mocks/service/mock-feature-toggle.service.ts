import { ActiveFeatureToggles, FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { BehaviorSubject, of } from 'rxjs';
import { distinctUntilChanged, map } from 'rxjs/operators';

export class MockFeatureToggleService {
    private subject = new BehaviorSubject<ActiveFeatureToggles>(Object.values(FeatureToggle));

    subscribeFeatureToggleUpdates() {}

    unsubscribeFeatureToggleUpdates() {}

    getFeatureToggles() {
        return this.subject;
    }

    getFeatureToggleActive(feature: FeatureToggle) {
        return this.subject.asObservable().pipe(
            map((activeFeatures) => activeFeatures.includes(feature)),
            distinctUntilChanged(),
        );
    }

    setFeatureToggleState(featureToggle: FeatureToggle, active: boolean) {
        const activeFeatureToggles = Object.values(FeatureToggle);

        if (!active) {
            const index = activeFeatureToggles.indexOf(featureToggle);
            if (index > -1) {
                activeFeatureToggles.splice(index, 1);
            }
        }

        return of(this.subject.next(activeFeatureToggles));
    }
}
