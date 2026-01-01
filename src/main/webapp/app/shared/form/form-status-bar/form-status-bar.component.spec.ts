import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormStatusBarComponent } from 'app/shared/form/form-status-bar/form-status-bar.component';

describe('FormStatusBarComponent', () => {
    let fixture: ComponentFixture<FormStatusBarComponent>;
    let comp: FormStatusBarComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [],
            providers: [],
        }).compileComponents();
        fixture = TestBed.createComponent(FormStatusBarComponent);
        comp = fixture.componentInstance;
    });

    beforeEach(() => {
        fixture.componentRef.setInput('formStatusSections', [
            { title: 'some-translation-key', valid: true },
            { title: 'another-translation-key', valid: false },
        ]);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should scroll to correct headline', () => {
        const mockDOMElement = { scrollIntoView: jest.fn(), style: {} };
        const getElementSpy = jest.spyOn(document, 'getElementById').mockReturnValue(mockDOMElement as any as HTMLElement);
        const scrollToSpy = jest.spyOn(mockDOMElement, 'scrollIntoView');
        comp.scrollToHeadline(comp.formStatusSections()[0].title);
        expect(getElementSpy).toHaveBeenCalledWith(comp.formStatusSections()[0].title);
        expect(scrollToSpy).toHaveBeenCalledOnce();
    });
});
