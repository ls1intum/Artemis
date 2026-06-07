import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TestCasePassedBuildsChartComponent } from 'app/programming/manage/grading/charts/test-case-passed-builds-chart.component';
import { TestCaseStats } from 'app/programming/shared/entities/programming-exercise-test-case-statistics.model';

describe('TestCasePassedBuildsChartComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: TestCasePassedBuildsChartComponent;
    let fixture: ComponentFixture<TestCasePassedBuildsChartComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        fixture = TestBed.createComponent(TestCasePassedBuildsChartComponent);
        comp = fixture.componentInstance;
    });

    const initComponent = (testCaseStats: TestCaseStats | undefined, totalParticipations: number) => {
        fixture.componentRef.setInput('testCaseStats', testCaseStats);
        fixture.componentRef.setInput('totalParticipations', totalParticipations);
    };

    it('should show a tooltip with "0% passed, 100% not executed" when receiving no stats', () => {
        initComponent(undefined, 5);

        expect(comp.tooltip()).toBe('0% passed, 0% failed, 100% not executed of 5 students.');
    });

    it('should show a tooltip with "40% passed, 60% failed" when receiving corresponding stats', () => {
        initComponent({ numPassed: 2, numFailed: 3 }, 5);

        expect(comp.tooltip()).toBe('40% passed, 60% failed of 5 students.');
    });

    it('should show a tooltip with "100% passed, 0% failed" when receiving corresponding stats', () => {
        initComponent({ numPassed: 5, numFailed: 0 }, 5);

        expect(comp.tooltip()).toBe('100% passed, 0% failed of 5 students.');
    });

    it('should show the default tooltip when given invalid inputs', () => {
        initComponent({ numPassed: -1, numFailed: 0 }, -1);

        expect(comp.tooltip()).toBe('0% passed, 0% failed, 100% not executed of 0 students.');
    });
});
