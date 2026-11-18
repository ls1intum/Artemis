import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormStatusBarComponent } from 'app/shared-ui/form/form-status-bar/form-status-bar.component';

describe('FormStatusBarComponent', () => {
    let fixture: ComponentFixture<FormStatusBarComponent>;
    let comp: FormStatusBarComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
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
        vi.restoreAllMocks();
    });

    it('labels every status circle with its translated section title', () => {
        vi.spyOn(TestBed.inject(TranslateService), 'instant').mockImplementation((key) => `Translated ${key}`);
        const scroll = vi.spyOn(comp, 'scrollToHeadline').mockImplementation(() => {});
        fixture.detectChanges();
        const circles = Array.from(fixture.nativeElement.querySelectorAll('.form-status-circle')) as HTMLElement[];
        expect(circles).toHaveLength(2);
        circles.forEach((circle, index) => {
            const title = comp.formStatusSections()[index].title;
            expect(circle.getAttribute('aria-label')).toBe(`Translated ${title}`);
            circle.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
            expect(scroll).toHaveBeenLastCalledWith(title);
        });
    });

    it('should scroll to correct headline', () => {
        const title = comp.formStatusSections()[0].title;
        const containerElement = { scrollTop: 100, getBoundingClientRect: vi.fn().mockReturnValue({ top: 0 }), scrollTo: vi.fn() } as any as HTMLElement;
        const targetElement = { style: {}, getBoundingClientRect: vi.fn().mockReturnValue({ top: 300 }), scrollIntoView: vi.fn() } as any as HTMLElement;

        const getElementSpy = vi.spyOn(document, 'getElementById').mockImplementation((id: string) => {
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
        const targetElement = { style: {}, getBoundingClientRect: vi.fn().mockReturnValue({ top: 300 }), scrollIntoView: vi.fn() } as any as HTMLElement;

        vi.spyOn(document, 'getElementById').mockImplementation((id: string) => {
            return id === title ? (targetElement as any) : null;
        });

        comp.scrollToHeadline(title);

        expect(targetElement.scrollIntoView).toHaveBeenCalledOnce();
    });
});
