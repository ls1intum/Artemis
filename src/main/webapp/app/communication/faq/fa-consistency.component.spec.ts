import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FaqConsistencyComponent } from 'app/communication/faq/faq-consistency.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('FaqConsistencyComponent', () => {
    let component: FaqConsistencyComponent;
    let fixture: ComponentFixture<FaqConsistencyComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FaqConsistencyComponent, TranslateDirective, FontAwesomeModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(FaqConsistencyComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should set formattedConsistency based on input values', () => {
        // arrange
        fixture.componentRef.setInput('suggestions', ['Suggestion 1', 'Suggestion 2']);
        fixture.componentRef.setInput('inconsistencies', ['FAQ 1', 'FAQ 2']);
        fixture.detectChanges();

        // act
        const result = component.formattedConsistency();

        // assert
        expect(result).toEqual([
            { inconsistentFaq: 'FAQ 1', suggestion: 'Suggestion 1' },
            { inconsistentFaq: 'FAQ 2', suggestion: 'Suggestion 2' },
        ]);
    });

    it('should correctly format inconsistencies when suggestions are present', () => {
        fixture.componentRef.setInput('suggestions', ['Suggestion 1', 'Suggestion 2']);
        fixture.componentRef.setInput('inconsistencies', ['FAQ 1', 'FAQ 2']);
        fixture.detectChanges();

        const result = component.formattedConsistency();
        expect(result).toEqual([
            {
                inconsistentFaq: 'FAQ 1',
                suggestion: 'Suggestion 1',
            },
            {
                inconsistentFaq: 'FAQ 2',
                suggestion: 'Suggestion 2',
            },
        ]);
    });

    it('should emit when dismissConsistencyCheck is called', () => {
        const spy = jest.fn();
        component.closeConsistencyWidget.subscribe(spy);

        component.dismissConsistencyCheck();
        expect(spy).toHaveBeenCalledOnce();
    });

    it('should handle undefined inputs gracefully', () => {
        fixture.componentRef.setInput('inconsistencies', undefined);
        fixture.componentRef.setInput('suggestions', undefined);
        fixture.detectChanges();
        expect(component.formattedConsistency()).toEqual([]);
    });
});
