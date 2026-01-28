import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { RunningJobsTableComponent } from './running-jobs-table.component';
import { BuildJob } from 'app/buildagent/shared/entities/build-job.model';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TriggeredByPushTo } from 'app/programming/shared/entities/repository-info.model';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';

describe('RunningJobsTableComponent', () => {
    setupTestBed({ zoneless: true });

    let component: RunningJobsTableComponent;
    let fixture: ComponentFixture<RunningJobsTableComponent>;

    const mockRunningJobs: BuildJob[] = [
        {
            id: '1',
            name: 'Build Job 1',
            buildAgent: { name: 'agent1', memberAddress: 'localhost:8080', displayName: 'Agent 1' },
            participationId: 101,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 3,
            repositoryInfo: {
                repositoryName: 'repo1',
                repositoryType: 'USER',
                triggeredByPushTo: TriggeredByPushTo.USER,
                assignmentRepositoryUri: 'https://some.uri',
                testRepositoryUri: 'https://some.uri',
                solutionRepositoryUri: 'https://some.uri',
                auxiliaryRepositoryUris: [],
                auxiliaryRepositoryCheckoutDirectories: [],
            },
            jobTimingInfo: {
                submissionDate: dayjs('2023-01-01'),
                buildStartDate: dayjs('2023-01-01'),
                buildCompletionDate: undefined,
                buildDuration: 120,
            },
            buildConfig: {
                dockerImage: 'someImage',
                commitHashToBuild: 'abc123',
                branch: 'main',
                programmingLanguage: 'Java',
                projectType: 'Maven',
                scaEnabled: false,
                sequentialTestRunsEnabled: false,
                resultPaths: [],
            },
        },
        {
            id: '2',
            name: 'Build Job 2 - Long Duration Warning',
            buildAgent: { name: 'agent2', memberAddress: 'localhost:8081', displayName: 'Agent 2' },
            participationId: 102,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 2,
            repositoryInfo: {
                repositoryName: 'repo2',
                repositoryType: 'USER',
                triggeredByPushTo: TriggeredByPushTo.USER,
                assignmentRepositoryUri: 'https://some.uri',
                testRepositoryUri: 'https://some.uri',
                solutionRepositoryUri: 'https://some.uri',
                auxiliaryRepositoryUris: [],
                auxiliaryRepositoryCheckoutDirectories: [],
            },
            jobTimingInfo: {
                submissionDate: dayjs('2023-01-01'),
                buildStartDate: dayjs('2023-01-01'),
                buildCompletionDate: undefined,
                buildDuration: 300, // > 240s, should show warning
            },
            buildConfig: {
                dockerImage: 'someImage',
                commitHashToBuild: 'abc124',
                branch: 'main',
                programmingLanguage: 'Java',
                projectType: 'Maven',
                scaEnabled: false,
                sequentialTestRunsEnabled: false,
                resultPaths: [],
            },
        },
    ];

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [RunningJobsTableComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({}) },
            ],
            schemas: [NO_ERRORS_SCHEMA],
        });

        await TestBed.compileComponents();

        fixture = TestBed.createComponent(RunningJobsTableComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display running jobs', () => {
        fixture.componentRef.setInput('buildJobs', mockRunningJobs);
        fixture.detectChanges();

        expect(component.buildJobs()).toEqual(mockRunningJobs);
    });

    it('should emit cancelJob event when cancel button is clicked', () => {
        fixture.componentRef.setInput('buildJobs', mockRunningJobs);
        fixture.detectChanges();

        const cancelJobSpy = vi.fn();
        component.cancelJob.subscribe(cancelJobSpy);

        const mockEvent = { stopPropagation: vi.fn() } as unknown as Event;
        component.onCancelJob('1', mockEvent);

        expect(cancelJobSpy).toHaveBeenCalledWith('1');
        expect(mockEvent.stopPropagation).toHaveBeenCalled();
    });

    it('should emit jobClick event when row is clicked', () => {
        fixture.componentRef.setInput('buildJobs', mockRunningJobs);
        fixture.detectChanges();

        const jobClickSpy = vi.fn();
        component.jobClick.subscribe(jobClickSpy);

        component.onJobClick('1');

        expect(jobClickSpy).toHaveBeenCalledWith('1');
    });

    it('should stop propagation when link is clicked', () => {
        const mockEvent = { stopPropagation: vi.fn() } as unknown as Event;
        component.onLinkClick(mockEvent);

        expect(mockEvent.stopPropagation).toHaveBeenCalled();
    });

    it('should show course ID column when showCourseId is true', () => {
        fixture.componentRef.setInput('buildJobs', mockRunningJobs);
        fixture.componentRef.setInput('showCourseId', true);
        fixture.detectChanges();

        expect(component.showCourseId()).toBe(true);
    });

    it('should hide course ID column when showCourseId is false', () => {
        fixture.componentRef.setInput('buildJobs', mockRunningJobs);
        fixture.componentRef.setInput('showCourseId', false);
        fixture.detectChanges();

        expect(component.showCourseId()).toBe(false);
    });

    it('should set isAdminView correctly', () => {
        fixture.componentRef.setInput('buildJobs', mockRunningJobs);
        fixture.componentRef.setInput('isAdminView', true);
        fixture.detectChanges();

        expect(component.isAdminView()).toBe(true);
    });

    it('should display empty state when no running jobs', () => {
        fixture.componentRef.setInput('buildJobs', []);
        fixture.detectChanges();

        expect(component.buildJobs()).toEqual([]);
    });
});
