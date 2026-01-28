import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { QueuedJobsTableComponent } from './queued-jobs-table.component';
import { BuildJob } from 'app/buildagent/shared/entities/build-job.model';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TriggeredByPushTo } from 'app/programming/shared/entities/repository-info.model';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';

describe('QueuedJobsTableComponent', () => {
    setupTestBed({ zoneless: true });

    let component: QueuedJobsTableComponent;
    let fixture: ComponentFixture<QueuedJobsTableComponent>;

    const mockQueuedJobs: BuildJob[] = [
        {
            id: '1',
            name: 'Queued Job 1',
            participationId: 101,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 4,
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
                submissionDate: dayjs().subtract(30, 'seconds'),
                buildStartDate: undefined,
                buildCompletionDate: undefined,
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
            name: 'Queued Job 2',
            participationId: 102,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 5,
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
                submissionDate: dayjs().subtract(2, 'minutes'),
                buildStartDate: undefined,
                buildCompletionDate: undefined,
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
            imports: [QueuedJobsTableComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({}) },
            ],
            schemas: [NO_ERRORS_SCHEMA],
        });

        await TestBed.compileComponents();

        fixture = TestBed.createComponent(QueuedJobsTableComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display queued jobs', () => {
        fixture.componentRef.setInput('buildJobs', mockQueuedJobs);
        fixture.detectChanges();

        expect(component.buildJobs()).toEqual(mockQueuedJobs);
    });

    it('should emit cancelJob event when cancel button is clicked', () => {
        fixture.componentRef.setInput('buildJobs', mockQueuedJobs);
        fixture.detectChanges();

        const cancelJobSpy = vi.fn();
        component.cancelJob.subscribe(cancelJobSpy);

        const mockEvent = { stopPropagation: vi.fn() } as unknown as Event;
        component.onCancelJob('1', mockEvent);

        expect(cancelJobSpy).toHaveBeenCalledWith('1');
        expect(mockEvent.stopPropagation).toHaveBeenCalled();
    });

    it('should emit jobClick event when row is clicked', () => {
        fixture.componentRef.setInput('buildJobs', mockQueuedJobs);
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
        fixture.componentRef.setInput('buildJobs', mockQueuedJobs);
        fixture.componentRef.setInput('showCourseId', true);
        fixture.detectChanges();

        expect(component.showCourseId()).toBe(true);
    });

    it('should hide course ID column when showCourseId is false', () => {
        fixture.componentRef.setInput('buildJobs', mockQueuedJobs);
        fixture.componentRef.setInput('showCourseId', false);
        fixture.detectChanges();

        expect(component.showCourseId()).toBe(false);
    });

    it('should calculate waiting time correctly', () => {
        const submissionDate = dayjs().subtract(90, 'seconds');
        const waitingTime = component.calculateWaitingTime(submissionDate);

        // Should be formatted as "1m Xs" since it's > 60 seconds
        expect(waitingTime).toMatch(/^1m \d+s$/);
    });

    it('should calculate waiting time for seconds under 60', () => {
        const submissionDate = dayjs().subtract(30, 'seconds');
        const waitingTime = component.calculateWaitingTime(submissionDate);

        // Should be formatted as "Xs" since it's < 60 seconds
        expect(waitingTime).toMatch(/^\d+s$/);
    });

    it('should return dash for undefined submission date', () => {
        const waitingTime = component.calculateWaitingTime(undefined);
        expect(waitingTime).toBe('-');
    });

    it('should display empty state when no queued jobs', () => {
        fixture.componentRef.setInput('buildJobs', []);
        fixture.detectChanges();

        expect(component.buildJobs()).toEqual([]);
    });
});
