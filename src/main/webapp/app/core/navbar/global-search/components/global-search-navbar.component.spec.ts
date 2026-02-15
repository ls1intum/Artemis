import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { MockPipe } from 'ng-mocks';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { GlobalSearchNavbarComponent } from './global-search-navbar.component';
import { OsDetectorService } from '../services/os-detector.service';
import { SearchOverlayService } from '../services/search-overlay.service';

describe('GlobalSearchNavbarComponent', () => {
    setupTestBed({ zoneless: true });

    let component: GlobalSearchNavbarComponent;
    let fixture: ComponentFixture<GlobalSearchNavbarComponent>;
    let searchOverlayService: SearchOverlayService;

    const mockSearchOverlayService = {
        isOpen: signal(false),
        open: vi.fn(),
        close: vi.fn(),
        toggle: vi.fn(),
    };

    const mockOsDetectorService = {
        actionKeyLabel: vi.fn(() => '⌘'),
        isMac: vi.fn(() => true),
        isActionKey: vi.fn(),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [GlobalSearchNavbarComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: SearchOverlayService, useValue: mockSearchOverlayService },
                { provide: OsDetectorService, useValue: mockOsDetectorService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });

        fixture = TestBed.createComponent(GlobalSearchNavbarComponent);
        component = fixture.componentInstance;
        searchOverlayService = TestBed.inject(SearchOverlayService);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('openSearch()', () => {
        it('should call searchOverlay.open()', () => {
            component.openSearch();
            expect(searchOverlayService.open).toHaveBeenCalled();
        });

        it('should open overlay when called multiple times', () => {
            component.openSearch();
            component.openSearch();
            expect(searchOverlayService.open).toHaveBeenCalledTimes(2);
        });
    });

    describe('Template Rendering', () => {
        it('should display search icon', () => {
            const icon = fixture.nativeElement.querySelector('fa-icon');
            expect(icon).toBeTruthy();
        });

        it('should display search text', () => {
            const searchText = fixture.nativeElement.querySelector('.search-text');
            expect(searchText).toBeTruthy();
        });

        it('should display keyboard hint', () => {
            const keyboardHint = fixture.nativeElement.querySelector('.keyboard-hint');
            expect(keyboardHint).toBeTruthy();
        });

        it('should be clickable', () => {
            const trigger = fixture.nativeElement.querySelector('.search-trigger');
            expect(trigger).toBeTruthy();
            expect(trigger.getAttribute('role')).toBe('button');
        });

        it('should have tabindex for keyboard navigation', () => {
            const trigger = fixture.nativeElement.querySelector('.search-trigger');
            expect(trigger.getAttribute('tabindex')).toBe('0');
        });
    });

    describe('Click Interaction', () => {
        it('should open search when clicked', () => {
            const trigger = fixture.nativeElement.querySelector('.search-trigger');
            trigger.click();
            expect(searchOverlayService.open).toHaveBeenCalled();
        });
    });

    describe('Keyboard Interaction', () => {
        it('should open search when Enter is pressed', () => {
            const trigger = fixture.nativeElement.querySelector('.search-trigger');
            const event = new KeyboardEvent('keydown', { key: 'Enter' });
            trigger.dispatchEvent(event);
            fixture.detectChanges();

            expect(searchOverlayService.open).toHaveBeenCalled();
        });

        it('should open search when Space is pressed', () => {
            const trigger = fixture.nativeElement.querySelector('.search-trigger');
            const event = new KeyboardEvent('keydown', { key: ' ' });
            trigger.dispatchEvent(event);
            fixture.detectChanges();

            expect(searchOverlayService.open).toHaveBeenCalled();
        });
    });

    describe('OS-Specific Display', () => {
        it('should display keyboard shortcut hint', () => {
            const keyboardHint = fixture.nativeElement.querySelector('.keyboard-hint');
            expect(keyboardHint).toBeTruthy();
            expect(keyboardHint.textContent).toBeTruthy();
        });

        it('should display action key from osDetector', () => {
            const keyboardHint = fixture.nativeElement.querySelector('.keyboard-hint');
            // Mock is set to return '⌘' for Mac
            expect(keyboardHint.textContent).toContain('⌘');
        });

        it('should display K key', () => {
            const keyboardHint = fixture.nativeElement.querySelector('.keyboard-hint');
            expect(keyboardHint.textContent).toContain('K');
        });
    });
});
