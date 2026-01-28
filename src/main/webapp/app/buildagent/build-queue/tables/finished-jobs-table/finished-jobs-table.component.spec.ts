import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { FinishedJobsTableComponent } from './finished-jobs-table.component';
import { FinishedBuildJob } from 'app/buildagent/shared/entities/build-job.model';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TriggeredByPushTo } from 'app/programming/shared/entities/repository-info.model';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';

describe('FinishedJobsTableComponent', () => {
    setupTestBed({ zoneless: true });

    let component: FinishedJobsTableComponent;
    let fixture: ComponentFixture<FinishedJobsTableComponent>;

    const mockFinishedJobs: FinishedBuildJob[] = [
        {
            id: '1',
            name: 'Finished Job 1',
            buildAgentAddress: 'agent1',
            participationId: 101,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 3,
            repositoryName: 'repo1',
            repositoryType: 'USER',
            triggeredByPushTo: TriggeredByPushTo.USER,
            buildSubmissionDate: dayjs('2023-01-01'),
            buildStartDate: dayjs('2023-01-01'),
            buildCompletionDate: dayjs('2023-01-01'),
            buildDuration: '12.5s',
            commitHash: 'abc123',
            status: 'SUCCESSFUL',
        },
        {
            id: '2',
            name: 'Finished Job 2',
            buildAgentAddress: 'agent2',
            participationId: 102,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 2,
            repositoryName: 'repo2',
            repositoryType: 'USER',
            triggeredByPushTo: TriggeredByPushTo.USER,
            buildSubmissionDate: dayjs('2023-01-02'),
            buildStartDate: dayjs('2023-01-02'),
            buildCompletionDate: dayjs('2023-01-02'),
            buildDuration: '1m 30s',
            commitHash: 'abc124',
            status: 'FAILED',
        },
        {
            id: '3',
            name: 'Finished Job 3',
            buildAgentAddress: 'agent3',
            participationId: 103,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 1,
            repositoryName: 'repo3',
            repositoryType: 'USER',
            triggeredByPushTo: TriggeredByPushTo.USER,
            buildSubmissionDate: dayjs('2023-01-03'),
            buildStartDate: dayjs('2023-01-03'),
            buildCompletionDate: dayjs('2023-01-03'),
            buildDuration: '45.0s',
            commitHash: 'abc125',
            status: 'CANCELLED',
        },
    ];

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [FinishedJobsTableComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({}) },
            ],
            schemas: [NO_ERRORS_SCHEMA],
        });

        await TestBed.compileComponents();

        fixture = TestBed.createComponent(FinishedJobsTableComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display finished jobs', () => {
        fixture.componentRef.setInput('buildJobs', mockFinishedJobs);
        fixture.detectChanges();

        expect(component.buildJobs()).toEqual(mockFinishedJobs);
    });

    it('should emit jobClick event when row is clicked', () => {
        fixture.componentRef.setInput('buildJobs', mockFinishedJobs);
        fixture.detectChanges();

        const jobClickSpy = vi.fn();
        component.jobClick.subscribe(jobClickSpy);

        component.onJobClick('1');

        expect(jobClickSpy).toHaveBeenCalledWith('1');
    });

    it('should not emit jobClick for undefined job ID', () => {
        fixture.componentRef.setInput('buildJobs', mockFinishedJobs);
        fixture.detectChanges();

        const jobClickSpy = vi.fn();
        component.jobClick.subscribe(jobClickSpy);

        component.onJobClick(undefined);

        expect(jobClickSpy).not.toHaveBeenCalled();
    });

    it('should emit viewLogs event when build logs link is clicked', () => {
        fixture.componentRef.setInput('buildJobs', mockFinishedJobs);
        fixture.detectChanges();

        const viewLogsSpy = vi.fn();
        component.viewLogs.subscribe(viewLogsSpy);

        const mockEvent = { stopPropagation: vi.fn() } as unknown as Event;
        component.onViewLogs(mockEvent, '1');

        expect(viewLogsSpy).toHaveBeenCalledWith('1');
        expect(mockEvent.stopPropagation).toHaveBeenCalled();
    });

    it('should not emit viewLogs for undefined job ID', () => {
        fixture.componentRef.setInput('buildJobs', mockFinishedJobs);
        fixture.detectChanges();

        const viewLogsSpy = vi.fn();
        component.viewLogs.subscribe(viewLogsSpy);

        const mockEvent = { stopPropagation: vi.fn() } as unknown as Event;
        component.onViewLogs(mockEvent, undefined);

        expect(viewLogsSpy).not.toHaveBeenCalled();
    });

    it('should emit sortChange event when sort is triggered', () => {
        fixture.componentRef.setInput('buildJobs', mockFinishedJobs);
        fixture.detectChanges();

        const sortChangeSpy = vi.fn();
        component.sortChange.subscribe(sortChangeSpy);

        component.onSortChange();

        expect(sortChangeSpy).toHaveBeenCalled();
    });

    it('should show course ID column when showCourseId is true', () => {
        fixture.componentRef.setInput('buildJobs', mockFinishedJobs);
        fixture.componentRef.setInput('showCourseId', true);
        fixture.detectChanges();

        expect(component.showCourseId()).toBe(true);
    });

    it('should hide course ID column when showCourseId is false', () => {
        fixture.componentRef.setInput('buildJobs', mockFinishedJobs);
        fixture.componentRef.setInput('showCourseId', false);
        fixture.detectChanges();

        expect(component.showCourseId()).toBe(false);
    });

    it('should set isAdminView correctly', () => {
        fixture.componentRef.setInput('buildJobs', mockFinishedJobs);
        fixture.componentRef.setInput('isAdminView', true);
        fixture.detectChanges();

        expect(component.isAdminView()).toBe(true);
    });

    it('should have default predicate as buildCompletionDate', () => {
        expect(component.predicate()).toBe('buildCompletionDate');
    });

    it('should have default ascending as false', () => {
        expect(component.ascending()).toBe(false);
    });

    it('should update predicate via two-way binding', () => {
        fixture.componentRef.setInput('predicate', 'name');
        fixture.detectChanges();

        expect(component.predicate()).toBe('name');
    });

    it('should update ascending via two-way binding', () => {
        fixture.componentRef.setInput('ascending', true);
        fixture.detectChanges();

        expect(component.ascending()).toBe(true);
    });

    it('should display empty state when no finished jobs', () => {
        fixture.componentRef.setInput('buildJobs', []);
        fixture.detectChanges();

        expect(component.buildJobs()).toEqual([]);
    });
});
