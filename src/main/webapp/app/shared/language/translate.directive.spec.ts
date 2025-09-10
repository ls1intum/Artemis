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

describe('TranslateDirective', () => {
    let fixture: ComponentFixture<TestTranslateDirectiveComponent>;
    let translateService: TranslateService;
    let spy: jest.SpyInstance;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TestTranslateDirectiveComponent],
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
});
