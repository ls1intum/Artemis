import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LandingNavbarComponent } from 'app/core/landing/landing-navbar.component';
import { LANGUAGES } from 'app/core/language/shared/language.constants';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('LandingNavbarComponent', () => {
    let fixture: ComponentFixture<LandingNavbarComponent>;
    let translateService: TranslateService;

    const menuEntries = () => Array.from(document.querySelectorAll<HTMLButtonElement>('[role="menuitem"]'));

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LandingNavbarComponent],
            providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }, { provide: ThemeService, useClass: MockThemeService }],
        }).compileComponents();

        translateService = TestBed.inject(TranslateService);
        fixture = TestBed.createComponent(LandingNavbarComponent);
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.destroy();
        vi.restoreAllMocks();
    });

    it('switches the language from the language menu', () => {
        const useSpy = vi.spyOn(translateService, 'use');
        fixture.nativeElement.querySelector('.lang-toggle').click();
        fixture.detectChanges();

        const entries = menuEntries();
        expect(entries.length).toBeGreaterThan(1);
        entries[1].click();
        fixture.detectChanges();

        expect(useSpy).toHaveBeenCalledWith(LANGUAGES[1]);
        expect(menuEntries()).toHaveLength(0);
    });

    it('closes the mobile menu after a language was chosen there', () => {
        const useSpy = vi.spyOn(translateService, 'use');
        const toggle: HTMLButtonElement = fixture.nativeElement.querySelector('.mobile-menu-btn');
        toggle.click();
        fixture.detectChanges();
        expect(toggle.getAttribute('aria-expanded')).toBe('true');

        fixture.nativeElement.querySelector('.mobile-nav .lang-toggle').click();
        fixture.detectChanges();
        menuEntries()[0].click();
        fixture.detectChanges();

        expect(useSpy).toHaveBeenCalledWith(LANGUAGES[0]);
        expect(toggle.getAttribute('aria-expanded')).toBe('false');
        expect(fixture.nativeElement.querySelector('.mobile-nav')).toBeNull();
    });

    it('navigates to the sign-in page', () => {
        const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
        fixture.nativeElement.querySelector('.login-btn').click();
        expect(navigateSpy).toHaveBeenCalledWith('/sign-in');
    });
});
