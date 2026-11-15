import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WritableSignal, inputBinding, signal } from '@angular/core';
import { DirectiveFixture, TestBed } from '@angular/core/testing';
import { ActiveMenuDirective } from './active-menu.directive';
import { LangChangeEvent, TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';

describe('ActiveMenuDirective', () => {
    let fixture: DirectiveFixture<ActiveMenuDirective>;
    let menuLanguage: WritableSignal<string | undefined>;
    let translateService: TranslateService;
    let langChangeSubject: Subject<LangChangeEvent>;

    beforeEach(() => {
        langChangeSubject = new Subject<LangChangeEvent>();

        const mockTranslateService = {
            onLangChange: langChangeSubject.asObservable(),
            getCurrentLang: vi.fn().mockReturnValue('en'),
        };

        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useValue: mockTranslateService }],
        });

        menuLanguage = signal<string | undefined>('en');
        fixture = TestBed.createDirective(ActiveMenuDirective, { tagName: 'div', bindings: [inputBinding('jhiActiveMenu', menuLanguage)] });
        translateService = TestBed.inject(TranslateService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create the directive', () => {
        fixture.detectChanges();
        expect(fixture.directiveInstance).toBeInstanceOf(ActiveMenuDirective);
    });

    it('should add active class when menu language matches current language', () => {
        menuLanguage.set('en');
        fixture.detectChanges();

        const divElement = fixture.nativeElement;
        expect(divElement.classList.contains('active')).toBe(true);
        expect(divElement.getAttribute('aria-pressed')).toBe('true');
    });

    it('should not add active class when menu language does not match current language', () => {
        menuLanguage.set('de');
        fixture.detectChanges();

        const divElement = fixture.nativeElement;
        expect(divElement.classList.contains('active')).toBe(false);
        expect(divElement.getAttribute('aria-pressed')).toBe('false');
    });

    it('should update active class when language changes to match menu language', async () => {
        menuLanguage.set('de');
        fixture.detectChanges();

        const divElement = fixture.nativeElement;
        expect(divElement.classList.contains('active')).toBe(false);
        expect(divElement.getAttribute('aria-pressed')).toBe('false');

        // Simulate language change to German
        langChangeSubject.next({ lang: 'de', translations: {} });
        await fixture.whenStable();
        fixture.detectChanges();

        expect(divElement.classList.contains('active')).toBe(true);
        expect(divElement.getAttribute('aria-pressed')).toBe('true');
    });

    it('should remove active class when language changes to not match menu language', async () => {
        menuLanguage.set('en');
        fixture.detectChanges();

        const divElement = fixture.nativeElement;
        expect(divElement.classList.contains('active')).toBe(true);
        expect(divElement.getAttribute('aria-pressed')).toBe('true');

        // Simulate language change to German
        langChangeSubject.next({ lang: 'de', translations: {} });
        await fixture.whenStable();
        fixture.detectChanges();

        expect(divElement.classList.contains('active')).toBe(false);
        expect(divElement.getAttribute('aria-pressed')).toBe('false');
    });

    it('should handle multiple language changes correctly', async () => {
        menuLanguage.set('en');
        fixture.detectChanges();

        const divElement = fixture.nativeElement;
        expect(divElement.classList.contains('active')).toBe(true);
        expect(divElement.getAttribute('aria-pressed')).toBe('true');

        // Change to German
        langChangeSubject.next({ lang: 'de', translations: {} });
        await fixture.whenStable();
        fixture.detectChanges();
        expect(divElement.classList.contains('active')).toBe(false);
        expect(divElement.getAttribute('aria-pressed')).toBe('false');

        // Change back to English
        langChangeSubject.next({ lang: 'en', translations: {} });
        await fixture.whenStable();
        fixture.detectChanges();
        expect(divElement.classList.contains('active')).toBe(true);
        expect(divElement.getAttribute('aria-pressed')).toBe('true');

        // Change to French
        langChangeSubject.next({ lang: 'fr', translations: {} });
        await fixture.whenStable();
        fixture.detectChanges();
        expect(divElement.classList.contains('active')).toBe(false);
        expect(divElement.getAttribute('aria-pressed')).toBe('false');
    });

    it('should call getCurrentLang on initialization', () => {
        fixture.detectChanges();
        expect(translateService.getCurrentLang).toHaveBeenCalled();
    });

    it('should handle undefined menu language gracefully', () => {
        menuLanguage.set(undefined);
        fixture.detectChanges();

        const divElement = fixture.nativeElement;
        // undefined !== 'en', so should not have active class
        expect(divElement.classList.contains('active')).toBe(false);
        expect(divElement.getAttribute('aria-pressed')).toBe('false');
    });

    it('should handle empty string menu language', async () => {
        menuLanguage.set('');
        fixture.detectChanges();

        const divElement = fixture.nativeElement;
        expect(divElement.classList.contains('active')).toBe(false);
        expect(divElement.getAttribute('aria-pressed')).toBe('false');

        // Even if language changes to empty string, should match
        langChangeSubject.next({ lang: '', translations: {} });
        await fixture.whenStable();
        fixture.detectChanges();
        expect(divElement.classList.contains('active')).toBe(true);
        expect(divElement.getAttribute('aria-pressed')).toBe('true');
    });
});
