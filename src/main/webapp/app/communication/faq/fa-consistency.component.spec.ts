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
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should build correct FAQ links from IDs and courseId', () => {
        fixture.componentRef.setInput('faqIds', [1, 2, 3]);
        fixture.componentRef.setInput('inconsistencies', ['Inconsistency 1', 'Inconsistency 2', 'Inconsistency 3']);
        fixture.componentRef.setInput('courseId', 42);
        fixture.detectChanges();

        expect(component.linksToFaqs).toEqual(['/courses/42/faq?faqId=1', '/courses/42/faq?faqId=2', '/courses/42/faq?faqId=3']);
    });

    it('should build empty links if no faqIds are set', () => {
        fixture.componentRef.setInput('faqIds', []);
        fixture.componentRef.setInput('courseId', 42);
        fixture.detectChanges();

        expect(component.linksToFaqs).toEqual([]);
    });

    it('should merge inconsistencies and links correctly', () => {
        fixture.componentRef.setInput('inconsistencies', ['Problem 1', 'Problem 2']);
        fixture.componentRef.setInput('faqIds', [11, 22]);
        fixture.componentRef.setInput('courseId', 5);
        fixture.detectChanges();

        expect(component.fullInconsistencyText).toEqual([
            'Problem 1 <a href="/courses/5/faq?faqId=11" target="_blank" rel="noopener noreferrer">#11</a>',
            'Problem 2 <a href="/courses/5/faq?faqId=22" target="_blank" rel="noopener noreferrer">#22</a>',
        ]);
    });

    it('should return raw inconsistency if faqId or link is missing', () => {
        // Manuell Methode testen, da effect bei inkonsistenter Länge Fehler wirft
        const result = (component as any).mergeInconsistencyWithLink('Problem ohne Link', undefined, undefined);
        expect(result).toBe('Problem ohne Link');
    });

    it('should throw if inconsistencies and faqIds have different lengths', () => {
        fixture.componentRef.setInput('inconsistencies', ['A', 'B', 'C']);
        fixture.componentRef.setInput('faqIds', [1, 2]);
        fixture.componentRef.setInput('courseId', 99);

        // Erwartung: Fehler wird geworfen
        expect(() => {
            fixture.detectChanges(); // triggert effect()
        }).toThrow('Inconsistencies and FAQ IDs arrays must have the same length');
    });

    it('should emit when dismissConsistencyCheck is called', () => {
        const spy = jest.fn();
        component.closeConsistencyWidget.subscribe(spy);

        component.dismissConsistencyCheck();
        expect(spy).toHaveBeenCalledOnce();
    });

    it('should build empty merged text when inputs are undefined', () => {
        fixture.componentRef.setInput('inconsistencies', undefined);
        fixture.componentRef.setInput('faqIds', undefined);
        fixture.componentRef.setInput('courseId', 1);
        fixture.detectChanges();

        expect(component.fullInconsistencyText).toEqual([]);
    });
});
