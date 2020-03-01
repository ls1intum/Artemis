import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { of } from 'rxjs';

export class MockFeatureToggleService {
    getFeatureToggleActive(toggle: FeatureToggle) {
        return of(true);
    }

    subscribeFeatureToggleUpdates() {}

    unsubscribeFeatureToggleUpdates() {}
}
