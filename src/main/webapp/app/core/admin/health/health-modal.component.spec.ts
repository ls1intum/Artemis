/**
 * Vitest tests for HealthModalComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { HealthModalComponent } from 'app/core/admin/health/health-modal.component';
import { HealthDetails, HealthKey } from 'app/core/admin/health/health.model';

describe('HealthModalComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<HealthModalComponent>;
    let comp: HealthModalComponent;
    let activeModal: NgbActiveModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [HealthModalComponent],
            providers: [NgbActiveModal],
        })
            .overrideTemplate(HealthModalComponent, '<button class="btn-close" (click)="dismiss()"></button>')
            .compileComponents();

        fixture = TestBed.createComponent(HealthModalComponent);
        comp = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);
    });

    it('should convert basic types to string', () => {
        expect(comp.readableValue(42)).toBe('42');
    });

    it('should stringify objects to parsable JSON', () => {
        const object = {
            foo: 'bar',
            bar: 42,
        };
        const result = comp.readableValue(object);
        expect(JSON.parse(result)).toEqual(object);
    });

    it('should parse GB-value to String for diskSpace', () => {
        comp.health.set({ key: 'diskSpace' as HealthKey, value: {} as HealthDetails });
        const gbValueInByte = 4156612385;
        const expectedString = '3.87 GB';
        expect(comp.readableValue(gbValueInByte)).toBe(expectedString);
    });

    it('should parse MB-value to String for diskSpace', () => {
        comp.health.set({ key: 'diskSpace' as HealthKey, value: {} as HealthDetails });
        const mbValueInByte = 41566;
        const expectedString = '0.04 MB';
        expect(comp.readableValue(mbValueInByte)).toBe(expectedString);
    });

    it('should recognize build agents arrays by key even when empty', () => {
        expect(comp.isBuildAgentsArray([], 'buildAgents')).toBe(true);
        expect(comp.isBuildAgentsArray([], 'notBuildAgents')).toBe(false);
        expect(comp.isBuildAgentsArray('notAnArray', 'buildAgents')).toBe(false);
    });

    it('should format simplified build agent data', () => {
        const buildAgents = [
            {
                name: 'agent-1',
                displayName: 'Agent One',
                memberAddress: '10.0.0.1',
                status: 'ACTIVE',
                currentJobs: 1,
                maxJobs: 4,
                runningJobs: ['job-1', 2],
            },
        ];

        const result = comp.formatBuildAgents(buildAgents, 'buildAgents');

        expect(result).toEqual([
            {
                displayName: 'Agent One',
                name: 'agent-1',
                memberAddress: '10.0.0.1',
                status: 'ACTIVE',
                currentJobs: 1,
                maxJobs: 4,
                runningJobNames: ['job-1', '2'],
            },
        ]);
    });

    it('should format legacy build agent data', () => {
        const buildAgents = [
            {
                buildAgent: {
                    name: 'legacy-agent',
                    displayName: 'Legacy Agent',
                    memberAddress: '192.168.0.1',
                },
                status: 'IDLE',
                numberOfCurrentBuildJobs: 0,
                maxNumberOfConcurrentBuildJobs: 2,
                runningBuildJobs: [{ name: 'job-a', id: 'job-a-id' }, { id: 'job-b-id' }],
                buildAgentDetails: {
                    gitRevision: 'abc123',
                    startDate: '2025-01-01T12:00:00Z',
                },
            },
        ];

        const result = comp.formatBuildAgents(buildAgents, 'buildAgents');

        expect(result).toEqual([
            {
                displayName: 'Legacy Agent',
                name: 'legacy-agent',
                memberAddress: '192.168.0.1',
                status: 'IDLE',
                currentJobs: 0,
                maxJobs: 2,
                runningJobNames: ['job-a', 'job-b-id'],
                gitRevision: 'abc123',
                startDate: '2025-01-01T12:00:00Z',
            },
        ]);
    });

    it('should dismiss the modal when dismiss is called', () => {
        const dismissSpy = vi.spyOn(activeModal, 'dismiss');

        comp.dismiss();

        expect(dismissSpy).toHaveBeenCalledOnce();
    });
});
