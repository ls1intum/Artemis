import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DetailOverviewNavigationBarComponent } from 'app/shared/detail-overview-navigation-bar/detail-overview-navigation-bar.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

const sectionHeadlines = [
    { id: 'general', translationKey: 'some.translation.key' },
    { id: 'grading', translationKey: 'another.translation.key' },
];

const headlineToScrollInto = {
    scrollIntoView: jest.fn(),
} as any as HTMLElement;

describe('DetailOverviewNavigationBar', () => {
    let component: DetailOverviewNavigationBarComponent;
    let fixture: ComponentFixture<DetailOverviewNavigationBarComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(DetailOverviewNavigationBarComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        component.sectionHeadlines = sectionHeadlines;
        fixture.changeDetectorRef.detectChanges();
        expect(DetailOverviewNavigationBarComponent).not.toBeNull();
    });

    it('should scroll into view', () => {
        // Mock getElementById only for this test, after component is created
        const getElementByIdSpy = jest.spyOn(document, 'getElementById').mockReturnValue(headlineToScrollInto);
        const scrollIntroViewSpy = jest.spyOn(headlineToScrollInto, 'scrollIntoView');

        component.sectionHeadlines = sectionHeadlines;
        component.scrollToView('general');

        expect(getElementByIdSpy).toHaveBeenCalledWith('general');
        expect(scrollIntroViewSpy).toHaveBeenCalledOnce();
    });
});
