import { Component } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
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
    let fixture: ComponentFixture<TestHostComponent>;
    let hostComponent: TestHostComponent;
    let translateService: TranslateService;
    let langChangeSubject: Subject<LangChangeEvent>;

    beforeEach(async () => {
        langChangeSubject = new Subject<LangChangeEvent>();

        const mockTranslateService = {
            onLangChange: langChangeSubject.asObservable(),
            getCurrentLang: jest.fn().mockReturnValue('en'),
        };

        await TestBed.configureTestingModule({
            imports: [TestHostComponent],
            providers: [{ provide: TranslateService, useValue: mockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TestHostComponent);
        hostComponent = fixture.componentInstance;
        translateService = TestBed.inject(TranslateService);
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
        expect(divElement.classList.contains('active')).toBeTrue();
    });

    it('should not add active class when menu language does not match current language', () => {
        hostComponent.menuLanguage = 'de';
        fixture.detectChanges();

        const divElement = fixture.nativeElement.querySelector('div');
        expect(divElement.classList.contains('active')).toBeFalse();
    });

    it('should update active class when language changes to match menu language', fakeAsync(() => {
        hostComponent.menuLanguage = 'de';
        fixture.detectChanges();

        const divElement = fixture.nativeElement.querySelector('div');
        expect(divElement.classList.contains('active')).toBeFalse();

        // Simulate language change to German
        langChangeSubject.next({ lang: 'de', translations: {} });
        tick();
        fixture.detectChanges();

        expect(divElement.classList.contains('active')).toBeTrue();
    }));

    it('should remove active class when language changes to not match menu language', fakeAsync(() => {
        hostComponent.menuLanguage = 'en';
        fixture.detectChanges();

        const divElement = fixture.nativeElement.querySelector('div');
        expect(divElement.classList.contains('active')).toBeTrue();

        // Simulate language change to German
        langChangeSubject.next({ lang: 'de', translations: {} });
        tick();
        fixture.detectChanges();

        expect(divElement.classList.contains('active')).toBeFalse();
    }));

    it('should handle multiple language changes correctly', fakeAsync(() => {
        hostComponent.menuLanguage = 'en';
        fixture.detectChanges();

        const divElement = fixture.nativeElement.querySelector('div');
        expect(divElement.classList.contains('active')).toBeTrue();

        // Change to German
        langChangeSubject.next({ lang: 'de', translations: {} });
        tick();
        fixture.detectChanges();
        expect(divElement.classList.contains('active')).toBeFalse();

        // Change back to English
        langChangeSubject.next({ lang: 'en', translations: {} });
        tick();
        fixture.detectChanges();
        expect(divElement.classList.contains('active')).toBeTrue();

        // Change to French
        langChangeSubject.next({ lang: 'fr', translations: {} });
        tick();
        fixture.detectChanges();
        expect(divElement.classList.contains('active')).toBeFalse();
    }));

    it('should call getCurrentLang on initialization', () => {
        fixture.detectChanges();
        expect(translateService.getCurrentLang).toHaveBeenCalled();
    });

    it('should handle undefined menu language gracefully', () => {
        hostComponent.menuLanguage = undefined as any;
        fixture.detectChanges();

        const divElement = fixture.nativeElement.querySelector('div');
        // undefined !== 'en', so should not have active class
        expect(divElement.classList.contains('active')).toBeFalse();
    });

    it('should handle empty string menu language', fakeAsync(() => {
        hostComponent.menuLanguage = '';
        fixture.detectChanges();

        const divElement = fixture.nativeElement.querySelector('div');
        expect(divElement.classList.contains('active')).toBeFalse();

        // Even if language changes to empty string, should match
        langChangeSubject.next({ lang: '', translations: {} });
        tick();
        fixture.detectChanges();
        expect(divElement.classList.contains('active')).toBeTrue();
    }));
});
