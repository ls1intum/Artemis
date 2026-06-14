/**
 * Centralized animation providers for tests.
 *
 * @swimlane/ngx-graph internally uses @angular/animations.
 * Until the atlas competency graphs are migrated away from ngx-graph, we need to provide animation support
 * in the tests that render it. (@swimlane/ngx-charts was migrated to PrimeNG charts and is no longer a reason
 * to keep this helper.)
 *
 * NOTE: As of PrimeNG 21 and Angular 21, PrimeNG components no longer require animation providers in tests.
 *
 * This file centralizes the deprecated provideNoopAnimations() call so that:
 * 1. The deprecation warning only appears in one place
 * 2. It's easy to find and remove once no longer needed
 * 3. All tests import from a consistent location
 *
 * TODO: Remove this file once ngx-graph no longer requires @angular/animations
 */

// eslint-disable-next-line @typescript-eslint/no-deprecated
import { provideNoopAnimations } from '@angular/platform-browser/animations';

/**
 * Provides noop animation support for tests that use components requiring @angular/animations.
 * Use this in TestBed.configureTestingModule({ providers: [..., provideNoopAnimationsForTests()] })
 *
 * Required for:
 * - @swimlane/ngx-graph (uses @animationState synthetic property)
 *
 * NOT required for:
 * - PrimeNG components (Dialog, Popover, Chart, etc.) - as of PrimeNG 21
 */
export function provideNoopAnimationsForTests() {
    return provideNoopAnimations();
}
