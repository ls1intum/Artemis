import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MetisDashboardLinkComponent } from 'app/metis/manage/dashboard/metis-dashboard-link.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('MetisDashboardLinkComponent', () => {
    let fixture: ComponentFixture<MetisDashboardLinkComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MetisDashboardLinkComponent],
            providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(MetisDashboardLinkComponent);
        fixture.componentRef.setInput('link', ['/course-management/6/programming-exercises/123', 'comprehension-check']);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function link(): HTMLAnchorElement {
        return fixture.nativeElement.querySelector('[data-testid="comprehension-check-button"]');
    }

    it('should link to the dashboard of the exercise with its label', () => {
        expect(link().getAttribute('href')).toBe('/course-management/6/programming-exercises/123/comprehension-check');
        expect(link().querySelector('[jhiTranslate="artemisApp.metis.dashboard.button"]')).not.toBeNull();
    });

    it('should take the size and the white label of the Bootstrap buttons of the toolbar it stands in', () => {
        // The buttons of the row stretch to the tallest one, so a taller link would make every button of its line taller
        expect([...link().classList]).toEqual(expect.arrayContaining(['px-2!', 'py-1!', 'text-[0.7875rem]!', 'leading-normal!', 'rounded-[0.1rem]!']));
        expect([...link().classList]).toContain('[--tumaet-ui-state-info-contrast:var(--white)]');
    });
});
