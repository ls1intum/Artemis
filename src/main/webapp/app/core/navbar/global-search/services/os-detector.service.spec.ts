import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { OsDetectorService } from './os-detector.service';

describe('OsDetectorService', () => {
    setupTestBed({ zoneless: true });

    let service: OsDetectorService;
    let originalNavigator: Navigator;

    beforeEach(() => {
        // Store original navigator
        originalNavigator = window.navigator;
    });

    afterEach(() => {
        // Restore original navigator
        Object.defineProperty(window, 'navigator', {
            value: originalNavigator,
            writable: true,
            configurable: true,
        });
    });

    function mockNavigator(platform: string, userAgent: string) {
        Object.defineProperty(window, 'navigator', {
            value: {
                ...originalNavigator,
                platform,
                userAgent,
            },
            writable: true,
            configurable: true,
        });
    }

    describe('Mac Detection', () => {
        beforeEach(() => {
            mockNavigator('MacIntel', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)');
            TestBed.configureTestingModule({
                providers: [OsDetectorService],
            });
            service = TestBed.inject(OsDetectorService);
        });

        it('should detect Mac from platform', () => {
            expect(service.isMac()).toBe(true);
        });

        it('should return ⌘ as action key label on Mac', () => {
            expect(service.actionKeyLabel()).toBe('⌘');
        });

        it('should detect metaKey as action key on Mac', () => {
            const event = new KeyboardEvent('keydown', { metaKey: true });
            expect(service.isActionKey(event)).toBe(true);
        });

        it('should not detect ctrlKey alone as action key on Mac', () => {
            const event = new KeyboardEvent('keydown', { ctrlKey: true });
            expect(service.isActionKey(event)).toBe(false);
        });

        it('should not detect no modifier as action key', () => {
            const event = new KeyboardEvent('keydown');
            expect(service.isActionKey(event)).toBe(false);
        });
    });

    describe('Windows Detection', () => {
        beforeEach(() => {
            mockNavigator('Win32', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)');
            TestBed.configureTestingModule({
                providers: [OsDetectorService],
            });
            service = TestBed.inject(OsDetectorService);
        });

        it('should detect Windows from platform', () => {
            expect(service.isMac()).toBe(false);
        });

        it('should return Ctrl as action key label on Windows', () => {
            expect(service.actionKeyLabel()).toBe('Ctrl');
        });

        it('should detect ctrlKey as action key on Windows', () => {
            const event = new KeyboardEvent('keydown', { ctrlKey: true });
            expect(service.isActionKey(event)).toBe(true);
        });

        it('should not detect metaKey alone as action key on Windows', () => {
            const event = new KeyboardEvent('keydown', { metaKey: true });
            expect(service.isActionKey(event)).toBe(false);
        });

        it('should not detect no modifier as action key', () => {
            const event = new KeyboardEvent('keydown');
            expect(service.isActionKey(event)).toBe(false);
        });
    });

    describe('Linux Detection', () => {
        beforeEach(() => {
            mockNavigator('Linux x86_64', 'Mozilla/5.0 (X11; Linux x86_64)');
            TestBed.configureTestingModule({
                providers: [OsDetectorService],
            });
            service = TestBed.inject(OsDetectorService);
        });

        it('should detect Linux as non-Mac', () => {
            expect(service.isMac()).toBe(false);
        });

        it('should return Ctrl as action key label on Linux', () => {
            expect(service.actionKeyLabel()).toBe('Ctrl');
        });

        it('should detect ctrlKey as action key on Linux', () => {
            const event = new KeyboardEvent('keydown', { ctrlKey: true });
            expect(service.isActionKey(event)).toBe(true);
        });
    });

    describe('iOS Detection', () => {
        beforeEach(() => {
            mockNavigator('iPhone', 'Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X)');
            TestBed.configureTestingModule({
                providers: [OsDetectorService],
            });
            service = TestBed.inject(OsDetectorService);
        });

        it('should detect iPhone as Mac-like', () => {
            expect(service.isMac()).toBe(true);
        });

        it('should return ⌘ as action key label on iPhone', () => {
            expect(service.actionKeyLabel()).toBe('⌘');
        });
    });

    describe('iPad Detection', () => {
        beforeEach(() => {
            mockNavigator('iPad', 'Mozilla/5.0 (iPad; CPU OS 14_0 like Mac OS X)');
            TestBed.configureTestingModule({
                providers: [OsDetectorService],
            });
            service = TestBed.inject(OsDetectorService);
        });

        it('should detect iPad as Mac-like', () => {
            expect(service.isMac()).toBe(true);
        });

        it('should return ⌘ as action key label on iPad', () => {
            expect(service.actionKeyLabel()).toBe('⌘');
        });
    });

    describe('Edge Cases', () => {
        it('should handle unknown platforms as non-Mac', () => {
            mockNavigator('Unknown', 'Unknown Browser');
            TestBed.configureTestingModule({
                providers: [OsDetectorService],
            });
            service = TestBed.inject(OsDetectorService);

            expect(service.isMac()).toBe(false);
            expect(service.actionKeyLabel()).toBe('Ctrl');
        });

        it('should handle both metaKey and ctrlKey pressed', () => {
            mockNavigator('MacIntel', 'Mac');
            TestBed.configureTestingModule({
                providers: [OsDetectorService],
            });
            service = TestBed.inject(OsDetectorService);

            const event = new KeyboardEvent('keydown', { metaKey: true, ctrlKey: true });
            // On Mac, metaKey takes precedence
            expect(service.isActionKey(event)).toBe(true);
        });
    });
});
