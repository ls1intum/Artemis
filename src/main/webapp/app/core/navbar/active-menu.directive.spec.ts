import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActiveMenuDirective } from './active-menu.directive';
import { LangChangeEvent, TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';

@Component({
    template: `<div [jhiActiveMenu]="menuLanguage"></div>`,
    imports: [ActiveMenuDirective],
})
class TestHostComponent {
    menuLanguage = 'en';
}

describe('ActiveMenuDirective', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TestHostComponent>;
    let hostComponent: TestHostComponent;
    let translateService: TranslateService;
    let langChangeSubject: Subject<LangChangeEvent>;

    beforeEach(async () => {
        langChangeSubject = new Subject<LangChangeEvent>();

        const mockTranslateService = {
            onLangChange: langChangeSubject.asObservable(),
            getCurrentLang: vi.fn().mockReturnValue('en'),
        };

        await TestBed.configureTestingModule({
            imports: [TestHostComponent],
            providers: [{ provide: TranslateService, useValue: mockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TestHostComponent);
        hostComponent = fixture.componentInstance;
        translateService = TestBed.inject(TranslateService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create the directive', () => {
        fixture.detectChanges();
        const divElement = fixture.nativeElement.querySelector('div');
        expect(divElement).toBeTruthy();
    });

    it('should add active class when menu language matches current language', () => {
        hostComponent.menuLanguage = 'en';
        fixture.detectChanges();

        const divElement = fixture.nativeElement.querySelector('div');
        expect(divElement.classList.contains('active')).toBe(true);
    });

    it('should not add active class when menu language does not match current language', () => {
        hostComponent.menuLanguage = 'de';
        fixture.detectChanges();

        const divElement = fixture.nativeElement.querySelector('div');
        expect(divElement.classList.contains('active')).toBe(false);
    });

    it('should update active class when language changes to match menu language', async () => {
        hostComponent.menuLanguage = 'de';
        fixture.detectChanges();

        const divElement = fixture.nativeElement.querySelector('div');
        expect(divElement.classList.contains('active')).toBe(false);

        // Simulate language change to German
        langChangeSubject.next({ lang: 'de', translations: {} });
        await fixture.whenStable();
        fixture.detectChanges();

        expect(divElement.classList.contains('active')).toBe(true);
    });

    it('should remove active class when language changes to not match menu language', async () => {
        hostComponent.menuLanguage = 'en';
        fixture.detectChanges();

        const divElement = fixture.nativeElement.querySelector('div');
        expect(divElement.classList.contains('active')).toBe(true);

        // Simulate language change to German
        langChangeSubject.next({ lang: 'de', translations: {} });
        await fixture.whenStable();
        fixture.detectChanges();

        expect(divElement.classList.contains('active')).toBe(false);
    });

    it('should handle multiple language changes correctly', async () => {
        hostComponent.menuLanguage = 'en';
        fixture.detectChanges();

        const divElement = fixture.nativeElement.querySelector('div');
        expect(divElement.classList.contains('active')).toBe(true);

        // Change to German
        langChangeSubject.next({ lang: 'de', translations: {} });
        await fixture.whenStable();
        fixture.detectChanges();
        expect(divElement.classList.contains('active')).toBe(false);

        // Change back to English
        langChangeSubject.next({ lang: 'en', translations: {} });
        await fixture.whenStable();
        fixture.detectChanges();
        expect(divElement.classList.contains('active')).toBe(true);

        // Change to French
        langChangeSubject.next({ lang: 'fr', translations: {} });
        await fixture.whenStable();
        fixture.detectChanges();
        expect(divElement.classList.contains('active')).toBe(false);
    });

    it('should call getCurrentLang on initialization', () => {
        fixture.detectChanges();
        expect(translateService.getCurrentLang).toHaveBeenCalled();
    });

    it('should handle undefined menu language gracefully', () => {
        hostComponent.menuLanguage = undefined as any;
        fixture.detectChanges();

        const divElement = fixture.nativeElement.querySelector('div');
        // undefined !== 'en', so should not have active class
        expect(divElement.classList.contains('active')).toBe(false);
    });

    it('should handle empty string menu language', async () => {
        hostComponent.menuLanguage = '';
        fixture.detectChanges();

        const divElement = fixture.nativeElement.querySelector('div');
        expect(divElement.classList.contains('active')).toBe(false);

        // Even if language changes to empty string, should match
        langChangeSubject.next({ lang: '', translations: {} });
        await fixture.whenStable();
        fixture.detectChanges();
        expect(divElement.classList.contains('active')).toBe(true);
    });
});
