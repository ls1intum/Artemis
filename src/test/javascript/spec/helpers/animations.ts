/**
 * Centralized animation providers for tests.
 *
 * Some third-party libraries (PrimeNG, ngx-charts, ngx-graph) internally use @angular/animations.
 * Until these libraries migrate to CSS animations, we need to provide animation support in tests.
 *
 * This file centralizes the deprecated provideNoopAnimations() call so that:
 * 1. The deprecation warning only appears in one place
 * 2. It's easy to find and remove once no longer needed
 * 3. All tests import from a consistent location
 *
 * TODO: Remove this file once PrimeNG and other dependencies no longer require @angular/animations
 */

// eslint-disable-next-line @typescript-eslint/no-deprecated
import { provideNoopAnimations } from '@angular/platform-browser/animations';

/**
 * Provides noop animation support for tests that use components requiring @angular/animations.
 * Use this in TestBed.configureTestingModule({ providers: [..., provideNoopAnimationsForTests()] })
 *
 * Required for:
 * - PrimeNG components (Dialog, Popover, etc.)
 * - @swimlane/ngx-charts
 * - @swimlane/ngx-graph
 */
export function provideNoopAnimationsForTests() {
    return provideNoopAnimations();
}
