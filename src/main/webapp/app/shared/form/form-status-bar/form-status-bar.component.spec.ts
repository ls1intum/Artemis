import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormStatusBarComponent } from 'app/shared/form/form-status-bar/form-status-bar.component';

describe('FormStatusBarComponent', () => {
    let fixture: ComponentFixture<FormStatusBarComponent>;
    let comp: FormStatusBarComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FormStatusBarComponent);
                comp = fixture.componentInstance;
            });
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
        const title = comp.formStatusSections()[0].title;
        const containerElement = { scrollTop: 100, getBoundingClientRect: jest.fn().mockReturnValue({ top: 0 }), scrollTo: jest.fn() } as any as HTMLElement;
        const targetElement = { style: {}, getBoundingClientRect: jest.fn().mockReturnValue({ top: 300 }), scrollIntoView: jest.fn() } as any as HTMLElement;

        const getElementSpy = jest.spyOn(document, 'getElementById').mockImplementation((id: string) => {
            if (id === 'course-body-container') {
                return containerElement;
            } else if (id === title) {
                return targetElement;
            }
            return null;
        });

        comp.scrollToHeadline(title);

        expect(getElementSpy).toHaveBeenCalledWith(title);
        expect(getElementSpy).toHaveBeenCalledWith('course-body-container');
        expect(containerElement.scrollTo).toHaveBeenCalledOnce();
    });

    it('should fall back to scrollIntoView when scroll container is missing', () => {
        const title = comp.formStatusSections()[0].title;
        const targetElement = { style: {}, getBoundingClientRect: jest.fn().mockReturnValue({ top: 300 }), scrollIntoView: jest.fn() } as any as HTMLElement;

        jest.spyOn(document, 'getElementById').mockImplementation((id: string) => {
            return id === title ? (targetElement as any) : null;
        });

        comp.scrollToHeadline(title);

        expect(targetElement.scrollIntoView).toHaveBeenCalledOnce();
    });
});
