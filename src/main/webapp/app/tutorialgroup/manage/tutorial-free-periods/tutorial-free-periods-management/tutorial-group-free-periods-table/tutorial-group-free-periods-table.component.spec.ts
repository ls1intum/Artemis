import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TutorialGroupFreePeriodsTableComponent } from './tutorial-group-free-periods-table.component';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import dayjs from 'dayjs/esm';
import { MockPipe } from 'ng-mocks';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('TutorialGroupFreePeriodsTableComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TutorialGroupFreePeriodsTableComponent;
    let fixture: ComponentFixture<TutorialGroupFreePeriodsTableComponent>;

    const course = { id: 1, timeZone: 'Europe/Berlin' } as Course;
    const tutorialGroupsConfiguration = new TutorialGroupsConfiguration();
    tutorialGroupsConfiguration.id = 1;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialGroupFreePeriodsTableComponent, MockPipe(ArtemisDatePipe), MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupFreePeriodsTableComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('tutorialGroupsConfiguration', tutorialGroupsConfiguration);
        fixture.componentRef.setInput('tutorialGroupFreePeriods', []);
        fixture.componentRef.setInput('labelText', 'Test Label');
        fixture.componentRef.setInput('loadAll', () => {});
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('isInThePast', () => {
        it('should return true when the free period end date is in the past', () => {
            const pastPeriod = new TutorialGroupFreePeriod();
            pastPeriod.id = 1;
            pastPeriod.end = dayjs().subtract(1, 'day');

            expect(component.isInThePast(pastPeriod)).toBe(true);
        });

        it('should return false when the free period end date is in the future', () => {
            const futurePeriod = new TutorialGroupFreePeriod();
            futurePeriod.id = 2;
            futurePeriod.end = dayjs().add(1, 'day');

            expect(component.isInThePast(futurePeriod)).toBe(false);
        });

        it('should return false when the free period end date is today', () => {
            const todayPeriod = new TutorialGroupFreePeriod();
            todayPeriod.id = 3;
            todayPeriod.end = dayjs().add(1, 'hour');

            expect(component.isInThePast(todayPeriod)).toBe(false);
        });
    });
});
