import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

@Component({
    template: '<div jhiTranslate="test"></div>',
    imports: [TranslateDirective],
})
class TestTranslateDirectiveComponent {}

@Component({
    template: '<div [jhiTranslate]="key"></div>',
    imports: [TranslateDirective],
})
class TestDynamicKeyTranslateDirectiveComponent {
    key?: string;
}

describe('TranslateDirective', () => {
    let fixture: ComponentFixture<TestTranslateDirectiveComponent>;
    let translateService: TranslateService;
    let spy: jest.SpyInstance;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TestTranslateDirectiveComponent, TestDynamicKeyTranslateDirectiveComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        translateService = TestBed.inject(TranslateService);
        spy = jest.spyOn(translateService, 'get');
        fixture = TestBed.createComponent(TestTranslateDirectiveComponent);
    });

    it('should change HTML', () => {
        fixture.detectChanges();

        expect(spy).toHaveBeenCalledWith('test', undefined);
    });

    it.each([undefined, ''])('should not call translateService.get for an empty key (%p) and should clear the element', (key) => {
        const dynamicFixture = TestBed.createComponent(TestDynamicKeyTranslateDirectiveComponent);
        dynamicFixture.componentInstance.key = key;
        const element: HTMLElement = dynamicFixture.nativeElement.querySelector('div');
        element.textContent = 'stale';

        // ngx-translate's get() throws synchronously on an empty key; the guard must prevent the call entirely
        expect(() => dynamicFixture.detectChanges()).not.toThrow();
        expect(spy).not.toHaveBeenCalled();
        expect(element.textContent).toBe('');
    });

    it('should translate once a previously empty key becomes non-empty', () => {
        const dynamicFixture = TestBed.createComponent(TestDynamicKeyTranslateDirectiveComponent);
        dynamicFixture.componentInstance.key = undefined;
        dynamicFixture.detectChanges();
        expect(spy).not.toHaveBeenCalled();

        dynamicFixture.componentInstance.key = 'test';
        dynamicFixture.detectChanges();
        expect(spy).toHaveBeenCalledWith('test', undefined);
    });
});
