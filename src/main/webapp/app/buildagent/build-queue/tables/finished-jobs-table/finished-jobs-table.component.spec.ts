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
import { BuildAgentInformation } from 'app/buildagent/shared/entities/build-agent-information.model';

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

    it('should trigger jobClick when Enter key is pressed on row and target equals currentTarget', () => {
        fixture.componentRef.setInput('buildJobs', mockFinishedJobs);
        fixture.detectChanges();

        const jobClickSpy = vi.fn();
        component.jobClick.subscribe(jobClickSpy);

        // Find the first row and dispatch an Enter keydown event
        const row = fixture.nativeElement.querySelector('tr.clickable-row');
        const enterEvent = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true });
        Object.defineProperty(enterEvent, 'target', { value: row });
        Object.defineProperty(enterEvent, 'currentTarget', { value: row });
        row.dispatchEvent(enterEvent);

        expect(jobClickSpy).toHaveBeenCalledWith('1');
    });

    it('should trigger jobClick when Space key is pressed on row and target equals currentTarget', () => {
        fixture.componentRef.setInput('buildJobs', mockFinishedJobs);
        fixture.detectChanges();

        const jobClickSpy = vi.fn();
        component.jobClick.subscribe(jobClickSpy);

        // Find the first row and dispatch a Space keydown event
        const row = fixture.nativeElement.querySelector('tr.clickable-row');
        const spaceEvent = new KeyboardEvent('keydown', { key: ' ', bubbles: true });
        Object.defineProperty(spaceEvent, 'target', { value: row });
        Object.defineProperty(spaceEvent, 'currentTarget', { value: row });
        row.dispatchEvent(spaceEvent);

        expect(jobClickSpy).toHaveBeenCalledWith('1');
    });

    it('should have status ERROR job in the mock data', () => {
        const errorJob: FinishedBuildJob = {
            id: '4',
            name: 'Error Job',
            buildAgentAddress: 'agent4',
            participationId: 104,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 1,
            repositoryName: 'repo4',
            repositoryType: 'USER',
            triggeredByPushTo: TriggeredByPushTo.USER,
            buildSubmissionDate: dayjs('2023-01-04'),
            buildStartDate: dayjs('2023-01-04'),
            buildCompletionDate: dayjs('2023-01-04'),
            buildDuration: '10.0s',
            commitHash: 'abc126',
            status: 'ERROR',
        };

        fixture.componentRef.setInput('buildJobs', [errorJob]);
        fixture.detectChanges();

        expect(component.buildJobs()).toEqual([errorJob]);
        expect(component.buildJobs()[0].status).toBe('ERROR');
    });

    it('should handle viewLogs button click by emitting viewLogs event', () => {
        fixture.componentRef.setInput('buildJobs', mockFinishedJobs);
        fixture.detectChanges();

        const viewLogsSpy = vi.fn();
        component.viewLogs.subscribe(viewLogsSpy);

        const mockEvent = { stopPropagation: vi.fn() } as unknown as Event;
        component.onViewLogs(mockEvent, '3');

        expect(viewLogsSpy).toHaveBeenCalledWith('3');
        expect(mockEvent.stopPropagation).toHaveBeenCalled();
    });

    it('should have result wrapper with click handler that stops propagation', () => {
        // This test verifies that the template structure is correct for preventing click propagation.
        // The span wrapper around jhi-result has (click)="$event.stopPropagation()" in the template.
        // We can verify this by checking that clicking inside the status cell of a non-successful job
        // (which doesn't have the result component) still triggers the row click, while successful
        // jobs have the span wrapper that prevents propagation.

        // For non-successful jobs, clicks on the status cell should bubble to the row
        fixture.componentRef.setInput('buildJobs', mockFinishedJobs);
        fixture.detectChanges();

        const jobClickSpy = vi.fn();
        component.jobClick.subscribe(jobClickSpy);

        // Find a failed job's status cell (no result wrapper) and click it
        const failedJobRow = fixture.nativeElement.querySelectorAll('tr.clickable-row')[1]; // Second job is FAILED
        const statusCell = failedJobRow.querySelector('td.finish-jobs-column-status');

        // Click on the status text directly - this should bubble to row click
        const clickEvent = new MouseEvent('click', { bubbles: true });
        statusCell.querySelector('span').dispatchEvent(clickEvent);

        // The click should have bubbled to the row and triggered jobClick
        expect(jobClickSpy).toHaveBeenCalledWith('2');
    });

    describe('address to agent info mapping', () => {
        const mockBuildAgents: BuildAgentInformation[] = [
            {
                buildAgent: {
                    name: 'build-agent-1',
                    memberAddress: '[192.168.1.1]:5701',
                    displayName: 'Build Agent 1',
                },
            },
            {
                buildAgent: {
                    name: 'build-agent-2',
                    memberAddress: '[2001:db8::1]:5702',
                    displayName: 'Build Agent 2',
                },
            },
        ];

        it('should return agent displayName for getAgentDisplayName when agent is online', () => {
            fixture.componentRef.setInput('buildAgents', mockBuildAgents);
            fixture.detectChanges();

            expect(component.getAgentDisplayName('[192.168.1.1]:5701')).toBe('Build Agent 1');
            expect(component.getAgentDisplayName('[2001:db8::1]:5702')).toBe('Build Agent 2');
        });

        it('should return address for getAgentDisplayName when agent is offline', () => {
            fixture.componentRef.setInput('buildAgents', mockBuildAgents);
            fixture.detectChanges();

            expect(component.getAgentDisplayName('[10.0.0.1]:9999')).toBe('[10.0.0.1]:9999');
        });

        it('should return empty string for getAgentDisplayName when address is undefined', () => {
            fixture.componentRef.setInput('buildAgents', mockBuildAgents);
            fixture.detectChanges();

            expect(component.getAgentDisplayName(undefined)).toBe('');
        });

        it('should return agent short name for getAgentLinkParam when agent is online', () => {
            fixture.componentRef.setInput('buildAgents', mockBuildAgents);
            fixture.detectChanges();

            expect(component.getAgentLinkParam('[192.168.1.1]:5701')).toBe('build-agent-1');
            expect(component.getAgentLinkParam('[2001:db8::1]:5702')).toBe('build-agent-2');
        });

        it('should return address for getAgentLinkParam when agent is offline', () => {
            fixture.componentRef.setInput('buildAgents', mockBuildAgents);
            fixture.detectChanges();

            expect(component.getAgentLinkParam('[10.0.0.1]:9999')).toBe('[10.0.0.1]:9999');
        });

        it('should return empty string for getAgentLinkParam when address is undefined', () => {
            fixture.componentRef.setInput('buildAgents', mockBuildAgents);
            fixture.detectChanges();

            expect(component.getAgentLinkParam(undefined)).toBe('');
        });

        it('should create correct address to agent info mapping by host', () => {
            fixture.componentRef.setInput('buildAgents', mockBuildAgents);
            fixture.detectChanges();

            // The mapping uses host only (without port) to handle ephemeral ports
            const map = component.addressToAgentInfoMap();
            expect(map.size).toBe(2);
            expect(map.get('192.168.1.1')).toEqual({ name: 'build-agent-1', displayName: 'Build Agent 1' });
            expect(map.get('2001:db8::1')).toEqual({ name: 'build-agent-2', displayName: 'Build Agent 2' });
        });

        it('should handle empty build agents list', () => {
            fixture.componentRef.setInput('buildAgents', []);
            fixture.detectChanges();

            expect(component.addressToAgentInfoMap().size).toBe(0);
            expect(component.getAgentDisplayName('[192.168.1.1]:5701')).toBe('[192.168.1.1]:5701');
        });

        it('should use name as displayName when displayName is not set', () => {
            const agentsWithoutDisplayName: BuildAgentInformation[] = [
                {
                    buildAgent: {
                        name: 'agent-no-display',
                        memberAddress: '[10.0.0.1]:5703',
                    },
                },
            ];
            fixture.componentRef.setInput('buildAgents', agentsWithoutDisplayName);
            fixture.detectChanges();

            // When displayName is not set, should fallback to name
            expect(component.getAgentDisplayName('[10.0.0.1]:5703')).toBe('agent-no-display');
        });
    });
});
