import { FeatureToggle } from 'app/feature-toggle';
import { of } from 'rxjs';

export class MockFeatureToggleService {
    getFeatureToggleActive(toggle: FeatureToggle) {
        return of(true);
    }

    subscribeFeatureToggleUpdates() {}

    unsubscribeFeatureToggleUpdates() {}
}
