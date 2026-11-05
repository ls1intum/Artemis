import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DetailOverviewNavigationBarComponent } from 'app/shared-ui/detail-overview-navigation-bar/detail-overview-navigation-bar.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Router, provideRouter } from '@angular/router';

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
            providers: [
                provideRouter([{ path: 'course-management/:courseId/text-exercises/:exerciseId', children: [] }]),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
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

    it('preserves the detail route and query parameters when navigating to a section', async () => {
        const router = TestBed.inject(Router);
        await router.navigateByUrl('/course-management/12/text-exercises/34?view=instructor');
        fixture.componentRef.setInput('sectionHeadlines', sectionHeadlines);
        fixture.detectChanges();
        const scroll = vi.spyOn(headlineToScrollInto, 'scrollIntoView');
        vi.spyOn(document, 'getElementById').mockReturnValue(headlineToScrollInto);
        const link = fixture.nativeElement.querySelector('a') as HTMLAnchorElement;
        expect(link.getAttribute('href')).toBe('/course-management/12/text-exercises/34?view=instructor#general');
        const click = new MouseEvent('click', { bubbles: true, cancelable: true });

        link.dispatchEvent(click);
        await fixture.whenStable();

        expect(click.defaultPrevented).toBe(true);
        expect(router.url).toBe('/course-management/12/text-exercises/34?view=instructor#general');
        expect(scroll).toHaveBeenCalledOnce();

        link.click();
        await fixture.whenStable();
        expect(scroll).toHaveBeenCalledTimes(2);
    });

    it.each([{ ctrlKey: true }, { metaKey: true }, { shiftKey: true }, { altKey: true }, { button: 1 }])(
        'does not scroll the originating page for a modified or nonprimary click: %o',
        async (options) => {
            fixture.componentRef.setInput('sectionHeadlines', sectionHeadlines);
            fixture.detectChanges();
            const scroll = vi.spyOn(headlineToScrollInto, 'scrollIntoView');
            vi.spyOn(document, 'getElementById').mockReturnValue(headlineToScrollInto);
            fixture.nativeElement.querySelector('a').dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, ...options }));
            await fixture.whenStable();
            expect(scroll).not.toHaveBeenCalled();
        },
    );

    it('scrolls again when returning from a fragment whose heading is not rendered', async () => {
        const router = TestBed.inject(Router);
        const scroll = vi.spyOn(headlineToScrollInto, 'scrollIntoView');
        vi.spyOn(document, 'getElementById').mockImplementation((id) => (id === 'general' ? headlineToScrollInto : null));
        fixture.componentRef.setInput('sectionHeadlines', sectionHeadlines);
        fixture.detectChanges();
        await router.navigateByUrl('/course-management/12/text-exercises/34#general');
        await fixture.whenStable();
        expect(scroll).toHaveBeenCalledOnce();

        await router.navigateByUrl('/course-management/12/text-exercises/34#grading');
        await fixture.whenStable();
        await router.navigateByUrl('/course-management/12/text-exercises/34#general');
        await fixture.whenStable();
        expect(scroll).toHaveBeenCalledTimes(2);
    });

    it('scrolls a copied fragment after its heading renders asynchronously and only once', async () => {
        const router = TestBed.inject(Router);
        await router.navigateByUrl('/course-management/12/text-exercises/34?view=instructor#grading');
        const getElementById = vi.spyOn(document, 'getElementById').mockReturnValue(null);
        fixture.componentRef.setInput('sectionHeadlines', sectionHeadlines);
        fixture.detectChanges();
        const scroll = vi.spyOn(headlineToScrollInto, 'scrollIntoView');
        await fixture.whenStable();
        expect(scroll).not.toHaveBeenCalled();

        getElementById.mockReturnValue(headlineToScrollInto);
        fixture.componentRef.setInput('sectionHeadlines', [...sectionHeadlines]);
        TestBed.tick();
        fixture.detectChanges();
        await fixture.whenStable();
        expect(getElementById).toHaveBeenCalledWith('grading');
        expect(scroll).toHaveBeenCalledOnce();

        fixture.detectChanges();
        await fixture.whenStable();
        expect(scroll).toHaveBeenCalledOnce();
    });
});
