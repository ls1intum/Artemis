import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DetailOverviewNavigationBarComponent } from 'app/shared-ui/detail-overview-navigation-bar/detail-overview-navigation-bar.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideRouter } from '@angular/router';

const sectionHeadlines = [
    { id: 'general', translationKey: 'some.translation.key' },
    { id: 'grading', translationKey: 'another.translation.key' },
];

const headlineToScrollInto = {
    scrollIntoView: vi.fn(),
} as any as HTMLElement;

describe('DetailOverviewNavigationBar', () => {
    let component: DetailOverviewNavigationBarComponent;
    let fixture: ComponentFixture<DetailOverviewNavigationBarComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(DetailOverviewNavigationBarComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.componentRef.setInput('sectionHeadlines', sectionHeadlines);
        fixture.changeDetectorRef.detectChanges();
        expect(DetailOverviewNavigationBarComponent).not.toBeNull();
    });

    it('should scroll into view', () => {
        // Mock getElementById only for this test, after component is created
        const getElementByIdSpy = vi.spyOn(document, 'getElementById').mockReturnValue(headlineToScrollInto);
        const scrollIntroViewSpy = vi.spyOn(headlineToScrollInto, 'scrollIntoView');

        fixture.componentRef.setInput('sectionHeadlines', sectionHeadlines);
        component.scrollToView('general');

        expect(getElementByIdSpy).toHaveBeenCalledWith('general');
        expect(scrollIntroViewSpy).toHaveBeenCalledOnce();
    });

    it('handles section links inside Angular instead of navigating to the base URL', () => {
        fixture.componentRef.setInput('sectionHeadlines', sectionHeadlines);
        fixture.detectChanges();
        const scroll = vi.spyOn(component, 'scrollToView');
        const click = new MouseEvent('click', { bubbles: true, cancelable: true });

        fixture.nativeElement.querySelector('a').dispatchEvent(click);

        expect(click.defaultPrevented).toBe(true);
        expect(scroll).toHaveBeenCalledWith('general');
    });
});
