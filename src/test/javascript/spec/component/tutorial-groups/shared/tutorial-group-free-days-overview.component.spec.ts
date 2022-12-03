import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialGroupFreeDaysOverviewComponent } from 'app/course/tutorial-groups/shared/tutorial-group-free-days-overview/tutorial-group-free-days-overview.component';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent, FaStackComponent } from '@fortawesome/angular-fontawesome';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { generateExampleTutorialGroupFreePeriod } from '../helpers/tutorialGroupFreePeriodExampleModel';
import dayjs from 'dayjs/esm';
import { SortService } from 'app/shared/service/sort.service';
import { Component, Input, IterableDiffers } from '@angular/core';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';

@Component({ selector: 'jhi-side-panel', template: '' })
class MockSidePanel {
    @Input() panelHeader: string;
    @Input() panelDescriptionHeader?: string;
}
describe('TutorialGroupFreeDaysOverviewComponent', () => {
    let component: TutorialGroupFreeDaysOverviewComponent;
    let fixture: ComponentFixture<TutorialGroupFreeDaysOverviewComponent>;

    let firstOfJanuaryPeriod: TutorialGroupFreePeriod;
    let thirdOfJanuaryPeriod: TutorialGroupFreePeriod;
    const currentDate = dayjs(new Date(Date.UTC(2021, 0, 2, 12, 0, 0)));

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [
                TutorialGroupFreeDaysOverviewComponent,
                MockComponent(FaStackComponent),
                MockComponent(FaIconComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(NgbPopover),
                MockSidePanel,
            ],
            providers: [SortService, IterableDiffers],
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

        component.tutorialGroupFreeDays = [{ ...firstOfJanuaryPeriod }, { ...thirdOfJanuaryPeriod }];
        component.timeZone = 'Europe/Berlin';
        jest.spyOn(component, 'getCurrentDate').mockReturnValue(currentDate);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should sort the free days by start date', () => {
        component.ngDoCheck();
        expect(component.tutorialGroupFreeDays).toEqual([thirdOfJanuaryPeriod, firstOfJanuaryPeriod]);
    });
});
