import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { FinishedBuildJobFilter, FinishedBuildsFilterModalComponent } from 'app/localci/build-queue/finished-builds-filter-modal/finished-builds-filter-modal.component';
import dayjs from 'dayjs/esm';
import { FinishedBuildJob } from 'app/localci/shared/entities/build-job.model';
import { TriggeredByPushTo } from 'app/programming/shared/entities/repository-info.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { TumAetUiDatePickerComponent } from '@tumaet/ui-angular';

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

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FinishedBuildsFilterModalComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
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
        fixture.componentRef.setInput('finishedBuildJobsInput', mockFinishedJobs);
        expect(component.buildAgentAddresses).toEqual(['agent5', 'agent6']);
    });

    it('should suggest all addresses on an empty query and filter on a term', () => {
        fixture.componentRef.setInput('finishedBuildJobsInput', mockFinishedJobs);

        component.searchBuildAgentAddresses({ query: '' });
        expect(component.buildAgentAddressSuggestions()).toEqual(['agent5', 'agent6']);

        component.searchBuildAgentAddresses({ query: 'agent5' });
        expect(component.buildAgentAddressSuggestions()).toEqual(['agent5']);
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

    it('should clone the incoming filter so edits stay isolated until confirmed', () => {
        const source = new FinishedBuildJobFilter('agent5');
        source.status = 'SUCCESSFUL';
        source.appliedFilters.set('someKey', true);
        source.numberOfAppliedFilters = 1;

        const localFixture = TestBed.createComponent(FinishedBuildsFilterModalComponent);
        const localComponent = localFixture.componentInstance;
        // Opening the dialog with an incoming filter clones it into the local working copy.
        localFixture.componentRef.setInput('finishedBuildJobFilterInput', source);
        localFixture.componentRef.setInput('visible', true);
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

    it('should emit the edited filter and hide when confirmed', () => {
        const emitted: FinishedBuildJobFilter[] = [];
        component.confirmed.subscribe((filter) => emitted.push(filter));
        component.finishedBuildJobFilter = new FinishedBuildJobFilter('agent9');

        component.confirm();

        expect(emitted).toHaveLength(1);
        expect(emitted[0].buildAgentAddress).toBe('agent9');
        expect(component.visible()).toBeFalsy();
    });

    it('should hide without emitting when cancelled', () => {
        const emitted: FinishedBuildJobFilter[] = [];
        component.confirmed.subscribe((filter) => emitted.push(filter));
        component.visible.set(true);

        component.cancel();

        expect(emitted).toHaveLength(0);
        expect(component.visible()).toBeFalsy();
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

    describe('date pickers', () => {
        const pickers = () =>
            fixture.debugElement.queryAll(By.directive(TumAetUiDatePickerComponent)).map((debugElement) => debugElement.componentInstance as TumAetUiDatePickerComponent);
        const from = () => pickers()[0];
        const to = () => pickers()[1];

        /** The pickers only exist while the dialog is open; opening it clones the filter the parent applies into the working copy. */
        const openDialog = (filter = new FinishedBuildJobFilter()) => {
            fixture.componentRef.setInput('finishedBuildJobFilterInput', filter);
            fixture.componentRef.setInput('visible', true);
            fixture.detectChanges();
        };

        it('should render a from and a to picker bound to the filter dates', () => {
            const filter = new FinishedBuildJobFilter();
            filter.buildSubmissionDateFilterFrom = dayjs('2023-01-01T10:00:00');
            filter.buildSubmissionDateFilterTo = dayjs('2023-01-02T10:00:00');
            openDialog(filter);

            expect(pickers()).toHaveLength(2);
            expect(from().inputId()).toBe('finished-builds-filter-from');
            expect(to().inputId()).toBe('finished-builds-filter-to');
            expect(from().value()!.isSame(dayjs('2023-01-01T10:00:00'))).toBe(true);
            expect(to().value()!.isSame(dayjs('2023-01-02T10:00:00'))).toBe(true);
            expect(from().shouldDisplayTimeZoneWarning()).toBe(false);
        });

        it('should write picked dates to the filter and track them as applied', () => {
            openDialog();
            from().value.set(dayjs('2023-01-01T10:00:00'));
            to().value.set(dayjs('2023-01-02T10:00:00'));

            expect(component.finishedBuildJobFilter.buildSubmissionDateFilterFrom!.isSame(dayjs('2023-01-01T10:00:00'))).toBe(true);
            expect(component.finishedBuildJobFilter.buildSubmissionDateFilterTo!.isSame(dayjs('2023-01-02T10:00:00'))).toBe(true);
            expect(component.finishedBuildJobFilter.numberOfAppliedFilters).toBe(2);
            expect(component.finishedBuildJobFilter.areDatesValid).toBe(true);

            from().value.set(undefined);

            expect(component.finishedBuildJobFilter.buildSubmissionDateFilterFrom).toBeUndefined();
            expect(component.finishedBuildJobFilter.numberOfAppliedFilters).toBe(1);
        });

        it('should mark both pickers invalid and show the message while the from date is not before the to date', () => {
            openDialog();
            to().value.set(dayjs('2023-01-01T10:00:00'));
            from().value.set(dayjs('2023-01-02T10:00:00'));
            fixture.detectChanges();

            expect(component.finishedBuildJobFilter.areDatesValid).toBe(false);
            expect(from().invalid()).toBe(true);
            expect(to().invalid()).toBe(true);
            expect(document.body.textContent).toContain('artemisApp.buildQueue.filter.buildSubmissionDate.invalidDate');

            from().value.set(dayjs('2022-12-31T10:00:00'));
            fixture.detectChanges();

            expect(from().invalid()).toBe(false);
            expect(to().invalid()).toBe(false);
            expect(document.body.textContent).not.toContain('artemisApp.buildQueue.filter.buildSubmissionDate.invalidDate');
        });

        it('should not apply a date whose text is not a date', () => {
            const emitted: FinishedBuildJobFilter[] = [];
            component.confirmed.subscribe((filter) => emitted.push(filter));
            openDialog();
            from().value.set(dayjs('2023-01-01T10:00:00'));
            to().value.set(dayjs('2023-01-02T10:00:00'));
            from().inputValidityChange.emit(false);

            component.confirm();

            expect(emitted).toHaveLength(1);
            expect(emitted[0].buildSubmissionDateFilterFrom).toBeUndefined();
            expect(emitted[0].buildSubmissionDateFilterTo!.isSame(dayjs('2023-01-02T10:00:00'))).toBe(true);
            expect(emitted[0].appliedFilters.has('artemis.buildQueue.finishedBuildJobFilterBuildSubmissionDateFilterFrom')).toBe(false);
            expect(emitted[0].areDatesValid).toBe(true);
        });
    });
});
