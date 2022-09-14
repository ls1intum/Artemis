import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    template: '<div jhiTranslate="test"></div>',
})
class TestTranslateDirectiveComponent {}

describe('TranslateDirective Tests', () => {
    let fixture: ComponentFixture<TestTranslateDirectiveComponent>;
    let translateService: TranslateService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot()],
            declarations: [TranslateDirective, TestTranslateDirectiveComponent],
        })
            .compileComponents()
            .then(() => {
                translateService = TestBed.inject(TranslateService);
                fixture = TestBed.createComponent(TestTranslateDirectiveComponent);
            });
    });

    it('should change HTML', () => {
        const spy = jest.spyOn(translateService, 'get');

        fixture.detectChanges();

        expect(spy).toHaveBeenCalledOnce();
    });
});
