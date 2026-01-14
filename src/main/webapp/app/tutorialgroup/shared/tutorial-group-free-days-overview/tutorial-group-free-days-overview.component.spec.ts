import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { generateExampleTutorialGroupFreePeriod } from 'test/helpers/sample/tutorialgroup/tutorialGroupFreePeriodExampleModel';
import dayjs from 'dayjs/esm';
import { SortService } from 'app/shared/service/sort.service';
import { IterableDiffers } from '@angular/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { TutorialGroupFreeDaysOverviewComponent } from 'app/tutorialgroup/shared/tutorial-group-free-days-overview/tutorial-group-free-days-overview.component';

describe('TutorialGroupFreeDaysOverviewComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TutorialGroupFreeDaysOverviewComponent;
    let fixture: ComponentFixture<TutorialGroupFreeDaysOverviewComponent>;

    let firstOfJanuaryPeriod: TutorialGroupFreePeriod;
    let thirdOfJanuaryPeriod: TutorialGroupFreePeriod;
    const currentDate = dayjs(new Date(Date.UTC(2021, 0, 2, 12, 0, 0)));

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialGroupFreeDaysOverviewComponent],
            providers: [SortService, IterableDiffers, { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupFreeDaysOverviewComponent);
        firstOfJanuaryPeriod = generateExampleTutorialGroupFreePeriod({
            id: 1,
            start: dayjs('2021-01-01T00:00:00.000Z'),
            end: dayjs('2021-01-01T23:59:59.000Z'),
            reason: 'First of January',
        });
        thirdOfJanuaryPeriod = generateExampleTutorialGroupFreePeriod({
            id: 3,
            start: dayjs('2021-01-03T00:00:00.000Z'),
            end: dayjs('2021-01-03T23:59:59.000Z'),
            reason: 'Third of January',
        });

        component = fixture.componentInstance;

        fixture.componentRef.setInput('tutorialGroupFreeDays', [{ ...firstOfJanuaryPeriod }, { ...thirdOfJanuaryPeriod }]);
        fixture.componentRef.setInput('timeZone', 'Europe/Berlin');
        vi.spyOn(component, 'getCurrentDate').mockReturnValue(currentDate);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should sort the free days by start date', () => {
        component.ngDoCheck();
        expect(component.tutorialGroupFreeDays()).toEqual([thirdOfJanuaryPeriod, firstOfJanuaryPeriod]);
    });
});
