import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { CardWrapperComponent } from './card-wrapper.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CardWrapperComponent', () => {
    let fixture: ComponentFixture<CardWrapperComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CardWrapperComponent],
            providers: [
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

    function card(): HTMLElement {
        return fixture.debugElement.query(By.css('[data-testid="card-wrapper"]')).nativeElement;
    }

    it('should display the translated title as the card header', () => {
        fixture.componentRef.setInput('title', 'test.title.key');
        fixture.detectChanges();

        const title = card().querySelector('.tumaet-ui-card-title');
        expect(title).toBeTruthy();
        expect(title!.textContent).toContain('test.title.key');
    });

    it('should apply default maxWidth and minHeight if not provided', () => {
        expect(card().style.maxWidth).toBe('50rem');
        expect(card().style.minHeight).toBe('90%');
    });

    it('should apply provided maxWidth style', () => {
        fixture.componentRef.setInput('maxWidth', '500px');
        fixture.detectChanges();

        expect(card().style.maxWidth).toBe('500px');
    });

    it('should apply provided minHeight style', () => {
        fixture.componentRef.setInput('minHeight', '50%');
        fixture.detectChanges();

        expect(card().style.minHeight).toBe('50%');
    });

    it('should have a content area for content projection', () => {
        expect(card().querySelector('.tumaet-ui-card-content')).toBeTruthy();
    });
});
