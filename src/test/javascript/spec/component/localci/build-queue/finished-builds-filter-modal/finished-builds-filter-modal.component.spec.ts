import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FinishedBuildJobFilter, FinishedBuildsFilterModalComponent } from 'app/localci/build-queue/finished-builds-filter-modal/finished-builds-filter-modal.component';
import { FinishedBuildJobFilterStorageKey } from 'app/localci/build-queue/finished-builds-filter-modal/finished-builds-filter-modal.component';
import dayjs from 'dayjs/esm';
import { FinishedBuildJob } from 'app/entities/programming/build-job.model';
import { TriggeredByPushTo } from 'app/entities/programming/repository-info.model';
import { LocalStorageService } from 'ngx-webstorage';
import { MockLocalStorageService } from '../../../../helpers/mocks/service/mock-local-storage.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';

describe('FinishedBuildsFilterModalComponent', () => {
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

    const mockLocalStorageService = new MockLocalStorageService();
    mockLocalStorageService.clear = (key?: string) => {
        if (key) {
            if (key in mockLocalStorageService.storage) {
                delete mockLocalStorageService.storage[key];
            }
        } else {
            mockLocalStorageService.storage = {};
        }
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule, FinishedBuildsFilterModalComponent],
            providers: [
                { provide: LocalStorageService, useValue: mockLocalStorageService },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(NgbActiveModal),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(FinishedBuildsFilterModalComponent);
        component = fixture.componentInstance;
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

        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.buildAgentAddress)).toBe('agent1');
        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.buildDurationFilterLowerBound)).toBe(1);
        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.buildDurationFilterUpperBound)).toBe(2);
        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.buildSubmissionDateFilterFrom)).toEqual(dayjs('2023-01-01'));
        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.buildSubmissionDateFilterTo)).toEqual(dayjs('2023-01-02'));
        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.status)).toBe('SUCCESSFUL');

        component.finishedBuildJobFilter = new FinishedBuildJobFilter();

        component.filterDurationChanged();
        component.filterDateChanged();
        component.filterBuildAgentAddressChanged();
        component.toggleBuildStatusFilter();

        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.buildAgentAddress)).toBeUndefined();
        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.buildDurationFilterLowerBound)).toBeUndefined();
        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.buildDurationFilterUpperBound)).toBeUndefined();
        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.buildSubmissionDateFilterFrom)).toBeUndefined();
        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.buildSubmissionDateFilterTo)).toBeUndefined();
        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.status)).toBeUndefined();
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
