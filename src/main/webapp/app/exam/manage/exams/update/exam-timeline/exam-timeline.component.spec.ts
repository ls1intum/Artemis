import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import dayjs from 'dayjs/esm';
import { beforeEach, describe, expect, it } from 'vitest';

import { ExamTimelineComponent } from 'app/exam/manage/exams/update/exam-timeline/exam-timeline.component';
import { ExamType } from 'app/exam/shared/entities/exam.model';
import { ExerciseTimelineStatus } from 'app/exercise/exercise-timeline/exercise-timeline.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExamTimelineComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ExamTimelineComponent;
    let fixture: ComponentFixture<ExamTimelineComponent>;
    let latestValidity: boolean | undefined;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamTimelineComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamTimelineComponent);
        component = fixture.componentInstance;
        latestValidity = undefined;
        component.examTimelineStatusChange.subscribe((valid) => (latestValidity = valid));
        setInputs({
            examType: ExamType.REAL,
            visibleFrom: undefined,
            startOfWorkingTime: undefined,
            startOfPracticeTime: undefined,
            endOfWorkingTime: undefined,
            workingTime: 0,
            gracePeriod: 180,
        });
        fixture.detectChanges();
    });

    const setInputs = (inputs: {
        examType?: ExamType;
        visibleFrom?: dayjs.Dayjs;
        startOfWorkingTime?: dayjs.Dayjs;
        startOfPracticeTime?: dayjs.Dayjs;
        endOfWorkingTime?: dayjs.Dayjs;
        workingTime?: number;
        gracePeriod?: number;
    }) => {
        Object.entries(inputs).forEach(([input, value]) => fixture.componentRef.setInput(input, value));
    };

    const markTimeline = (status: ExerciseTimelineStatus) => {
        component.timelineStatus.set(status);
        fixture.detectChanges();
    };

    it('should calculate the working time for real exams correctly', () => {
        component.endOfWorkingTime.set(dayjs().add(2, 'hours'));
        fixture.detectChanges();

        expect(component.workingTime()).toBe(0);

        component.startOfWorkingTime.set(dayjs());
        component.endOfWorkingTime.set(component.startOfWorkingTime()!.add(2, 'hours'));
        fixture.detectChanges();

        expect(component.workingTime()).toBe(7200);

        component.endOfWorkingTime.set(undefined);
        fixture.detectChanges();

        expect(component.workingTime()).toBe(0);
    });

    it('should not calculate the working time for practice test exams', () => {
        setInputs({
            examType: ExamType.PRACTICE,
            workingTime: 3600,
            startOfWorkingTime: dayjs(),
            endOfWorkingTime: dayjs().add(12, 'hours'),
        });
        fixture.detectChanges();

        expect(component.workingTime()).toBe(3600);
        expect(component.noWorkingTimeNeeded()).toBe(false);
    });

    it('should calculate the working time for simulation test exams correctly', () => {
        const start = dayjs();
        setInputs({
            examType: ExamType.SIMULATION,
            workingTime: 3600,
            startOfWorkingTime: start,
            endOfWorkingTime: start.add(2, 'hours'),
        });
        fixture.detectChanges();

        expect(component.workingTime()).toBe(7200);
        expect(component.noWorkingTimeNeeded()).toBe(true);
    });

    it('should not calculate the working time for simulation and practice test exams', () => {
        const start = dayjs();
        setInputs({
            examType: ExamType.SIMULATION_AND_PRACTICE,
            workingTime: 3600,
            startOfWorkingTime: start,
            endOfWorkingTime: start.add(2, 'hours'),
        });
        fixture.detectChanges();

        expect(component.workingTime()).toBe(3600);
        expect(component.noWorkingTimeNeeded()).toBe(false);
    });

    it('should derive the simulation end date for simulation and practice test exams', () => {
        const start = dayjs().startOf('minute');
        setInputs({
            examType: ExamType.SIMULATION_AND_PRACTICE,
            workingTime: 3600,
            startOfWorkingTime: start,
        });
        fixture.detectChanges();

        expect(component.endOfSimulationTime()?.isSame(start.add(1, 'hour'))).toBe(true);
        expect(component.timelineItems().map((item) => item.labelStringKey)).toEqual([
            'artemisApp.examManagement.visibleDate',
            'artemisApp.examManagement.testExam.startDate',
            'artemisApp.examManagement.testExam.simulationEndDate',
            'artemisApp.examManagement.testExam.practiceStartDate',
            'artemisApp.examManagement.testExam.endDate',
        ]);

        component.workingTime.set(7200);
        fixture.detectChanges();

        expect(component.endOfSimulationTime()?.isSame(start.add(2, 'hours'))).toBe(true);
    });

    it('should use the correct timeline labels for real and test exams', () => {
        expect(component.timelineItems().map((item) => item.labelStringKey)).toEqual([
            'artemisApp.examManagement.visibleDate',
            'artemisApp.examManagement.startDate',
            'artemisApp.examManagement.endDate',
        ]);

        setInputs({ examType: ExamType.PRACTICE });
        fixture.detectChanges();

        expect(component.timelineItems().map((item) => item.labelStringKey)).toEqual([
            'artemisApp.examManagement.visibleDate',
            'artemisApp.examManagement.testExam.startDate',
            'artemisApp.examManagement.testExam.endDate',
        ]);
    });

    it('validates the working time for practice test exams correctly', () => {
        setInputs({ examType: ExamType.PRACTICE, workingTime: undefined });
        markTimeline({ valid: true, empty: false });
        expect(latestValidity).toBe(false);

        setInputs({ startOfWorkingTime: undefined, endOfWorkingTime: undefined });
        markTimeline({ valid: true, empty: false });
        expect(latestValidity).toBe(false);

        const start = dayjs();
        setInputs({ visibleFrom: start.subtract(1, 'hour'), startOfWorkingTime: start, workingTime: 3600, endOfWorkingTime: start.subtract(2, 'hours') });
        markTimeline({ valid: true, empty: false });
        expect(latestValidity).toBe(false);

        setInputs({ endOfWorkingTime: start.add(2, 'hours') });
        markTimeline({ valid: true, empty: false });
        expect(latestValidity).toBe(true);

        setInputs({ workingTime: 7200 });
        markTimeline({ valid: true, empty: false });
        expect(latestValidity).toBe(true);

        setInputs({ workingTime: 10800 });
        markTimeline({ valid: true, empty: false });
        expect(latestValidity).toBe(false);
    });

    it('validates the working time for simulation test exams like real exams', () => {
        setInputs({ examType: ExamType.SIMULATION, workingTime: undefined, startOfWorkingTime: undefined, endOfWorkingTime: undefined });
        markTimeline({ valid: false, empty: true });
        expect(latestValidity).toBe(false);

        setInputs({ workingTime: 3600 });
        markTimeline({ valid: false, empty: true });
        expect(latestValidity).toBe(false);

        const start = dayjs();
        setInputs({ visibleFrom: start.subtract(1, 'hour'), startOfWorkingTime: start });
        markTimeline({ valid: false, empty: false });
        expect(latestValidity).toBe(false);

        setInputs({ endOfWorkingTime: start.add(1, 'hour') });
        markTimeline({ valid: true, empty: false });
        expect(latestValidity).toBe(true);
        expect(component.workingTime()).toBe(3600);
    });

    it('validates the working time for real exams correctly', () => {
        setInputs({ workingTime: undefined, startOfWorkingTime: undefined, endOfWorkingTime: undefined });
        markTimeline({ valid: false, empty: true });
        expect(latestValidity).toBe(false);

        setInputs({ workingTime: 3600 });
        markTimeline({ valid: false, empty: true });
        expect(latestValidity).toBe(false);

        const start = dayjs();
        setInputs({ visibleFrom: start.subtract(1, 'hour'), startOfWorkingTime: start });
        markTimeline({ valid: false, empty: false });
        expect(latestValidity).toBe(false);

        setInputs({ endOfWorkingTime: start.add(1, 'hour') });
        markTimeline({ valid: true, empty: false });
        expect(latestValidity).toBe(true);
        expect(component.workingTime()).toBe(3600);
    });

    it('should emit invalid when timeline dates are invalid', () => {
        setInputs({
            visibleFrom: dayjs(),
            startOfWorkingTime: dayjs().add(2, 'hours'),
            endOfWorkingTime: dayjs().add(1, 'hour'),
        });
        markTimeline({ valid: false, empty: false });

        expect(latestValidity).toBe(false);
    });

    it('should show a warning when the visible date is more than 4 hours before the start date', () => {
        setInputs({
            visibleFrom: dayjs(),
            startOfWorkingTime: dayjs().add(240, 'minute'),
        });
        fixture.detectChanges();

        expect(component.showVisibleFromWarning()).toBe(false);

        component.startOfWorkingTime.set(dayjs().add(241, 'minute'));
        fixture.detectChanges();

        expect(component.showVisibleFromWarning()).toBe(true);
    });

    it('should not show the visible date warning when either date is missing', () => {
        setInputs({ visibleFrom: dayjs(), startOfWorkingTime: undefined });
        fixture.detectChanges();

        expect(component.showVisibleFromWarning()).toBe(false);

        setInputs({ visibleFrom: undefined, startOfWorkingTime: dayjs() });
        fixture.detectChanges();

        expect(component.showVisibleFromWarning()).toBe(false);
    });

    it('should correctly validate grace period with upper limit of 3600 seconds', () => {
        markTimeline({ valid: true, empty: false });
        expect(latestValidity).toBe(true);

        setInputs({ gracePeriod: 3600 });
        markTimeline({ valid: true, empty: false });
        expect(latestValidity).toBe(true);

        setInputs({ gracePeriod: 3601 });
        markTimeline({ valid: true, empty: false });
        expect(latestValidity).toBe(false);

        setInputs({ gracePeriod: -1 });
        markTimeline({ valid: true, empty: false });
        expect(latestValidity).toBe(false);
    });

    it('should clamp the maximum working time for practice exams', () => {
        setInputs({ examType: ExamType.PRACTICE, startOfWorkingTime: undefined, endOfWorkingTime: undefined });
        fixture.detectChanges();
        expect(component.maxWorkingTimeInMinutes()).toBe(43200);

        const start = dayjs();
        setInputs({ startOfWorkingTime: start, endOfWorkingTime: start.subtract(1, 'hour') });
        fixture.detectChanges();
        expect(component.maxWorkingTimeInMinutes()).toBe(0);

        setInputs({ endOfWorkingTime: start.add(2, 'hours') });
        fixture.detectChanges();
        expect(component.maxWorkingTimeInMinutes()).toBe(120);

        setInputs({ endOfWorkingTime: start.add(40, 'days') });
        fixture.detectChanges();
        expect(component.maxWorkingTimeInMinutes()).toBe(43200);
    });

    it('should correctly validate working time with upper limit of 30 days', () => {
        const start = dayjs();
        setInputs({
            examType: ExamType.PRACTICE,
            visibleFrom: start.subtract(1, 'hour'),
            startOfWorkingTime: start,
            endOfWorkingTime: start.add(35, 'days'),
            workingTime: 86400,
        });
        markTimeline({ valid: true, empty: false });
        expect(latestValidity).toBe(true);

        setInputs({ workingTime: 2592000 });
        markTimeline({ valid: true, empty: false });
        expect(latestValidity).toBe(true);

        setInputs({ workingTime: 2592001 });
        markTimeline({ valid: true, empty: false });
        expect(latestValidity).toBe(false);
    });
});
