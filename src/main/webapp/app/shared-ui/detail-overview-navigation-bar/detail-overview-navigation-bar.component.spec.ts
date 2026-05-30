import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { DetailOverviewNavigationBarComponent } from 'app/shared-ui/detail-overview-navigation-bar/detail-overview-navigation-bar.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

const sectionHeadlines = [
    { id: 'general', translationKey: 'some.translation.key' },
    { id: 'grading', translationKey: 'another.translation.key' },
];

const headlineToScrollInto = {
    scrollIntoView: vi.fn(),
} as any as HTMLElement;

describe('DetailOverviewNavigationBar', () => {
    setupTestBed({ zoneless: true });
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
});
