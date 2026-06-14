/**
 * Centralized animation providers for tests.
 *
 * Some component tests still require @angular/animations providers. This was originally needed
 * for @swimlane/ngx-charts and @swimlane/ngx-graph, both of which have been replaced.
 *
 * NOTE: As of PrimeNG 21 and Angular 21, PrimeNG components no longer require animation providers in tests.
 *
 * This file centralizes the deprecated provideNoopAnimations() call so that:
 * 1. The deprecation warning only appears in one place
 * 2. It's easy to find and remove once no longer needed
 * 3. All tests import from a consistent location
 *
 * TODO: Remove this file once no component tests require @angular/animations
 */

// eslint-disable-next-line @typescript-eslint/no-deprecated
import { provideNoopAnimations } from '@angular/platform-browser/animations';

/**
 * Provides noop animation support for tests that use components requiring @angular/animations.
 * Use this in TestBed.configureTestingModule({ providers: [..., provideNoopAnimationsForTests()] })
 *
 * NOT required for:
 * - PrimeNG components (Dialog, Popover, Chart, etc.) - as of PrimeNG 21
 */
export function provideNoopAnimationsForTests() {
    return provideNoopAnimations();
}
