import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { CardWrapperComponent } from './card-wrapper.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockJhiTranslateDirective } from 'test/helpers/mocks/directive/mock-jhi-translate-directive.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CardWrapperComponent', () => {
    let fixture: ComponentFixture<CardWrapperComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CardWrapperComponent],
            providers: [
                {
                    provide: TranslateDirective,
                    useClass: MockJhiTranslateDirective,
                },
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CardWrapperComponent);
        fixture.componentRef.setInput('title', 'test.title');
        fixture.detectChanges();
    });

    it('should display the title using jhiTranslate directive', () => {
        const testTitle = 'test.title.key';
        fixture.componentRef.setInput('title', testTitle);
        fixture.detectChanges();

        const cardHeaderElement = fixture.debugElement.query(By.css('.card-header'));
        expect(cardHeaderElement).toBeTruthy();
        const titleH5Element = fixture.debugElement.query(By.css('h5'));
        expect(titleH5Element).toBeTruthy();
        expect(titleH5Element.nativeElement.textContent).toBe('test.title.key');
    });

    it('should apply default maxWidth and minHeight if not provided', () => {
        fixture.componentRef.setInput('title', 'test.title');
        fixture.detectChanges();

        const cardElement = fixture.debugElement.query(By.css('.card'));
        expect(cardElement.nativeElement.style.maxWidth).toBe('50rem');
        expect(cardElement.nativeElement.style.minHeight).toBe('90%');
    });

    it('should apply provided maxWidth style', () => {
        const testMaxWidth = '500px';
        fixture.componentRef.setInput('title', 'test.title');
        fixture.componentRef.setInput('maxWidth', testMaxWidth);
        fixture.detectChanges();

        const cardElement = fixture.debugElement.query(By.css('.card'));
        expect(cardElement.nativeElement.style.maxWidth).toBe(testMaxWidth);
    });

    it('should apply provided minHeight style', () => {
        const testMinHeight = '50%';

        fixture.componentRef.setInput('minHeight', testMinHeight);
        fixture.detectChanges();

        const cardElement = fixture.debugElement.query(By.css('.card'));
        expect(cardElement.nativeElement.style.minHeight).toBe(testMinHeight);
    });

    it('should have a card-body for content projection', () => {
        fixture.componentRef.setInput('title', 'test.title');
        fixture.detectChanges();

        const cardBodyElement = fixture.debugElement.query(By.css('.card-body'));
        expect(cardBodyElement).toBeTruthy();
    });
});
