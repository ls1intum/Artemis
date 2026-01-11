import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseManagementOverviewStatisticsComponent } from 'app/core/course/manage/overview/course-management-overview-statistics.component';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { ComponentRef } from '@angular/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { provideNoopAnimationsForTests } from 'test/helpers/animations';

describe('CourseManagementOverviewStatisticsComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CourseManagementOverviewStatisticsComponent>;
    let component: CourseManagementOverviewStatisticsComponent;
    let componentRef: ComponentRef<CourseManagementOverviewStatisticsComponent>;

    const amountOfStudentsInCourse = 25;
    const initialStats = [0, 11, 9, 23];
    const course = { startDate: dayjs().subtract(5, 'weeks'), endDate: dayjs().add(5, 'weeks') } as Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                provideNoopAnimationsForTests(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseManagementOverviewStatisticsComponent);
                component = fixture.componentInstance;
                componentRef = fixture.componentRef;
                componentRef.setInput('course', course);
                componentRef.setInput('amountOfStudentsInCourse', amountOfStudentsInCourse);
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize component and load values', async () => {
        // Provide the input data
        componentRef.setInput('initialStats', initialStats);

        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.ngxData()).toHaveLength(1);
        expect(component.ngxData()[0].name).toBe('active students');
        expect(component.ngxData()[0].series[0].value).toBe(0);
        expect(component.ngxData()[0].series[1].value).toBe(44);
        expect(component.ngxData()[0].series[2].value).toBe(36);
        expect(component.ngxData()[0].series[3].value).toBe(92);
    });

    it('should react to changes', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        componentRef.setInput('initialStats', []);
        componentRef.setInput('amountOfStudentsInCourse', 0);

        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.ngxData()[0].series[0].value).toBe(0);
        expect(component.ngxData()[0].series[1].value).toBe(0);
        expect(component.ngxData()[0].series[2].value).toBe(0);
        expect(component.ngxData()[0].series[3].value).toBe(0);
    });

    it('should show lettering if course did not start yet', async () => {
        componentRef.setInput('course', { startDate: dayjs().add(1, 'week') } as Course);
        componentRef.setInput('initialStats', []);

        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.startDateAlreadyPassed).toBe(false);
    });

    it('should show only 2 weeks if start date is 1 week ago', async () => {
        componentRef.setInput('course', { startDate: dayjs().subtract(1, 'week') } as Course);
        componentRef.setInput('initialStats', initialStats.slice(2));

        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.ngxData()[0].series).toHaveLength(2);
        expect(component.ngxData()[0].series[0].value).toBe(36);
        expect(component.ngxData()[0].series[1].value).toBe(92);
    });

    it('should adapt labels if end date is passed', async () => {
        componentRef.setInput('course', { endDate: dayjs().subtract(1, 'week') } as Course);

        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.ngxData()[0].series[3].name).toBe('overview.weekAgo');
    });

    it('should adapt if course phase is smaller than 4 weeks', async () => {
        componentRef.setInput('course', { startDate: dayjs().subtract(2, 'weeks'), endDate: dayjs().subtract(1, 'weeks') } as Course);
        componentRef.setInput('initialStats', initialStats.slice(2));

        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.ngxData()[0].series).toHaveLength(2);
        expect(component.ngxData()[0].series[0].value).toBe(36);
        expect(component.ngxData()[0].series[1].value).toBe(92);
        expect(component.ngxData()[0].series[1].name).toBe('overview.weekAgo');
    });
});
