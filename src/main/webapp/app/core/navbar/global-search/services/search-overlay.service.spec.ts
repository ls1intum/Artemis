import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { SearchOverlayService } from './search-overlay.service';

describe('SearchOverlayService', () => {
    setupTestBed({ zoneless: true });

    let service: SearchOverlayService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [SearchOverlayService],
        });
        service = TestBed.inject(SearchOverlayService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('isOpen signal', () => {
        it('should initialize with false', () => {
            expect(service.isOpen()).toBe(false);
        });
    });

    describe('open()', () => {
        it('should set isOpen signal to true', () => {
            service.open();
            expect(service.isOpen()).toBe(true);
        });

        it('should set body overflow to hidden', () => {
            service.open();
            expect(document.body.style.overflow).toBe('hidden');
        });

        it('should keep isOpen true if called multiple times', () => {
            service.open();
            service.open();
            expect(service.isOpen()).toBe(true);
        });
    });

    describe('close()', () => {
        beforeEach(() => {
            // Open the overlay first
            service.open();
        });

        it('should set isOpen signal to false', () => {
            service.close();
            expect(service.isOpen()).toBe(false);
        });

        it('should reset body overflow to empty string', () => {
            service.close();
            expect(document.body.style.overflow).toBe('');
        });

        it('should keep isOpen false if called multiple times', () => {
            service.close();
            service.close();
            expect(service.isOpen()).toBe(false);
        });

        it('should work when overlay is already closed', () => {
            service.close();
            expect(service.isOpen()).toBe(false);

            // Call close again
            service.close();
            expect(service.isOpen()).toBe(false);
            expect(document.body.style.overflow).toBe('');
        });
    });

    describe('toggle()', () => {
        it('should open overlay when closed', () => {
            expect(service.isOpen()).toBe(false);
            service.toggle();
            expect(service.isOpen()).toBe(true);
            expect(document.body.style.overflow).toBe('hidden');
        });

        it('should close overlay when open', () => {
            service.open();
            expect(service.isOpen()).toBe(true);

            service.toggle();
            expect(service.isOpen()).toBe(false);
            expect(document.body.style.overflow).toBe('');
        });

        it('should toggle multiple times correctly', () => {
            service.toggle(); // open
            expect(service.isOpen()).toBe(true);

            service.toggle(); // close
            expect(service.isOpen()).toBe(false);

            service.toggle(); // open
            expect(service.isOpen()).toBe(true);
        });
    });

    describe('body overflow management', () => {
        it('should handle rapid open/close cycles', () => {
            service.open();
            service.close();
            service.open();
            service.close();

            expect(service.isOpen()).toBe(false);
            expect(document.body.style.overflow).toBe('');
        });

        it('should handle toggle cycles', () => {
            service.toggle();
            service.toggle();
            service.toggle();

            expect(service.isOpen()).toBe(true);
            expect(document.body.style.overflow).toBe('hidden');
        });
    });
});
