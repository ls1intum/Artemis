/**
 * Vitest Angular Testing Utilities for Artemis
 *
 * Provides reusable test helpers for Angular testing with Vitest.
 * TestBed is initialized globally in test-setup.ts - no need to call initTestBed().
 *
 * @example Basic usage:
 * ```typescript
 * import { ZonelessTestModule } from 'test-vitest/test-utils';
 *
 * describe('MyService', () => {
 *   beforeEach(() => {
 *     TestBed.configureTestingModule({
 *       imports: [ZonelessTestModule], // Optional - already provided globally
 *     });
 *   });
 * });
 * ```
 */
import { NgModule, provideZonelessChangeDetection } from '@angular/core';

/**
 * Module that provides zoneless change detection for all tests.
 * This enables modern signal-based testing without Zone.js.
 *
 * NOTE: This is already provided globally via test-setup.ts.
 * Only import this if you need to explicitly add it to a test module.
 */
@NgModule({
    providers: [provideZonelessChangeDetection()],
})
export class ZonelessTestModule {}
