import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { FinishedBuildJobFilter, FinishedBuildsFilterModalComponent } from 'app/localci/build-queue/finished-builds-filter-modal/finished-builds-filter-modal.component';
import dayjs from 'dayjs/esm';
import { FinishedBuildJob } from 'app/localci/shared/entities/build-job.model';
import { TriggeredByPushTo } from 'app/programming/shared/entities/repository-info.model';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';

describe('FinishedBuildsFilterModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: FinishedBuildsFilterModalComponent;
    let fixture: ComponentFixture<FinishedBuildsFilterModalComponent>;

    const mockFinishedJobs: FinishedBuildJob[] = [
        {
            id: '5',
            name: 'Build Job 5',
            buildAgentAddress: 'agent5',
            participationId: 105,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 1,
            repositoryName: 'repo5',
            repositoryType: 'USER',
            triggeredByPushTo: TriggeredByPushTo.USER,
            buildSubmissionDate: dayjs('2023-01-05'),
            buildStartDate: dayjs('2023-01-05'),
            buildCompletionDate: dayjs('2023-01-05'),
            buildDuration: undefined,
            commitHash: 'abc127',
        },
        {
            id: '6',
            name: 'Build Job 6',
            buildAgentAddress: 'agent6',
            participationId: 106,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 0,
            repositoryName: 'repo6',
            repositoryType: 'USER',
            triggeredByPushTo: TriggeredByPushTo.USER,
            buildStartDate: dayjs('2023-01-06'),
            buildCompletionDate: dayjs('2023-01-06'),
            buildDuration: undefined,
            commitHash: 'abc128',
        },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule, FinishedBuildsFilterModalComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, MockProvider(DynamicDialogRef), { provide: DynamicDialogConfig, useValue: { data: {} } }],
        }).compileComponents();

        fixture = TestBed.createComponent(FinishedBuildsFilterModalComponent);
        component = fixture.componentInstance;
        component.finishedBuildJobFilter = new FinishedBuildJobFilter();
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should return correct build agent addresses', () => {
        component.finishedBuildJobs = mockFinishedJobs;
        expect(component.buildAgentAddresses).toEqual(['agent5', 'agent6']);
    });

    it('should return correct number of filters applied', () => {
        component.finishedBuildJobFilter = new FinishedBuildJobFilter();
        component.finishedBuildJobFilter.buildAgentAddress = 'agent1';
        component.filterBuildAgentAddressChanged();
        component.finishedBuildJobFilter.buildDurationFilterLowerBound = 1;
        component.finishedBuildJobFilter.buildDurationFilterUpperBound = 2;
        component.filterDurationChanged();
        component.finishedBuildJobFilter.buildSubmissionDateFilterFrom = dayjs('2023-01-01');
        component.finishedBuildJobFilter.buildSubmissionDateFilterTo = dayjs('2023-01-02');
        component.filterDateChanged();
        component.toggleBuildStatusFilter('SUCCESSFUL');

        expect(component.finishedBuildJobFilter.numberOfAppliedFilters).toBe(6);

        component.finishedBuildJobFilter.buildAgentAddress = undefined;
        component.filterBuildAgentAddressChanged();
        component.finishedBuildJobFilter.buildDurationFilterLowerBound = undefined;
        component.filterDurationChanged();

        expect(component.finishedBuildJobFilter.numberOfAppliedFilters).toBe(4);
    });

    it('should save filter in local storage', () => {
        component.finishedBuildJobFilter = new FinishedBuildJobFilter();
        component.finishedBuildJobFilter.buildAgentAddress = 'agent1';
        component.finishedBuildJobFilter.buildDurationFilterLowerBound = 1;
        component.finishedBuildJobFilter.buildDurationFilterUpperBound = 2;
        component.finishedBuildJobFilter.buildSubmissionDateFilterFrom = dayjs('2023-01-01');
        component.finishedBuildJobFilter.buildSubmissionDateFilterTo = dayjs('2023-01-02');
        component.finishedBuildJobFilter.status = 'SUCCESSFUL';

        component.filterDurationChanged();
        component.filterDateChanged();
        component.filterBuildAgentAddressChanged();
        component.toggleBuildStatusFilter('SUCCESSFUL');

        expect(component.finishedBuildJobFilter.appliedFilters.size).toBe(6);

        component.finishedBuildJobFilter = new FinishedBuildJobFilter();

        component.filterDurationChanged();
        component.filterDateChanged();
        component.filterBuildAgentAddressChanged();
        component.toggleBuildStatusFilter();

        expect(component.finishedBuildJobFilter.appliedFilters.size).toBe(0);
    });

    it('should clone the incoming filter so edits stay isolated until confirmed', async () => {
        const source = new FinishedBuildJobFilter('agent5');
        source.status = 'SUCCESSFUL';
        source.appliedFilters.set('someKey', true);
        source.numberOfAppliedFilters = 1;

        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule, FinishedBuildsFilterModalComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(DynamicDialogRef),
                { provide: DynamicDialogConfig, useValue: { data: { finishedBuildJobFilter: source } } },
            ],
        }).compileComponents();

        const localFixture = TestBed.createComponent(FinishedBuildsFilterModalComponent);
        const localComponent = localFixture.componentInstance;
        localFixture.detectChanges();

        // The component must work on a clone, not the parent's instance
        expect(localComponent.finishedBuildJobFilter).not.toBe(source);
        expect(localComponent.finishedBuildJobFilter.appliedFilters).not.toBe(source.appliedFilters);
        expect(localComponent.finishedBuildJobFilter.buildAgentAddress).toBe('agent5');
        expect(localComponent.finishedBuildJobFilter.status).toBe('SUCCESSFUL');
        expect(localComponent.finishedBuildJobFilter.appliedFilters.get('someKey')).toBeTruthy();

        // Editing the clone (e.g. before a cancel) must not leak back into the parent's filter
        localComponent.finishedBuildJobFilter.status = 'FAILED';
        localComponent.finishedBuildJobFilter.appliedFilters.set('anotherKey', true);
        expect(source.status).toBe('SUCCESSFUL');
        expect(source.appliedFilters.has('anotherKey')).toBeFalsy();
    });

    it('should validate correctly', () => {
        component.finishedBuildJobFilter = new FinishedBuildJobFilter();
        component.finishedBuildJobFilter.buildDurationFilterLowerBound = 1;
        component.finishedBuildJobFilter.buildSubmissionDateFilterFrom = dayjs('2023-01-01');
        component.filterDurationChanged();
        component.filterDateChanged();

        expect(component.finishedBuildJobFilter.areDatesValid).toBeTruthy();
        expect(component.finishedBuildJobFilter.areDurationFiltersValid).toBeTruthy();

        component.finishedBuildJobFilter.buildDurationFilterUpperBound = 2;
        component.finishedBuildJobFilter.buildSubmissionDateFilterTo = dayjs('2023-01-02');
        component.filterDurationChanged();
        component.filterDateChanged();

        expect(component.finishedBuildJobFilter.areDatesValid).toBeTruthy();
        expect(component.finishedBuildJobFilter.areDurationFiltersValid).toBeTruthy();

        component.finishedBuildJobFilter.buildDurationFilterLowerBound = 3;
        component.finishedBuildJobFilter.buildSubmissionDateFilterFrom = dayjs('2023-01-03');
        component.filterDurationChanged();
        component.filterDateChanged();

        expect(component.finishedBuildJobFilter.areDatesValid).toBeFalsy();
        expect(component.finishedBuildJobFilter.areDurationFiltersValid).toBeFalsy();
    });
});
