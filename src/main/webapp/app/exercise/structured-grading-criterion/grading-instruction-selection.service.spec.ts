import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { GradingInstructionSelectionHost, GradingInstructionSelectionService } from 'app/exercise/structured-grading-criterion/grading-instruction-selection.service';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';

describe('GradingInstructionSelectionService', () => {
    let service: GradingInstructionSelectionService;
    let host: GradingInstructionSelectionHost;
    const instruction = { id: 1, credits: 2 } as GradingInstruction;

    beforeEach(() => {
        service = TestBed.inject(GradingInstructionSelectionService);
        host = {
            appliedInstructionIds: signal<ReadonlySet<number>>(new Set([1])),
            appliedInstructionCounts: signal<ReadonlyMap<number, number>>(new Map([[1, 1]])),
            removableInstructionIds: signal<ReadonlySet<number>>(new Set([1])),
            applyInstruction: vi.fn(),
            unapplyOneInstruction: vi.fn(),
            unapplyInstruction: vi.fn(),
        };
    });

    it('should report nothing as selectable or applied without a registered host', () => {
        expect(service.isSelectable()).toBe(false);
        expect(service.appliedInstructionIds().size).toBe(0);
        expect(service.isApplied(instruction)).toBe(false);
        expect(service.isRemovable(instruction)).toBe(false);
        expect(service.applicationCount(instruction)).toBe(0);
    });

    it('should not report an instruction that the host cannot take back as removable', () => {
        service.register({
            appliedInstructionIds: signal<ReadonlySet<number>>(new Set([1])),
            appliedInstructionCounts: signal<ReadonlyMap<number, number>>(new Map([[1, 2]])),
            removableInstructionIds: signal<ReadonlySet<number>>(new Set()),
            applyInstruction: vi.fn(),
            unapplyOneInstruction: vi.fn(),
            unapplyInstruction: vi.fn(),
        });

        expect(service.isApplied(instruction)).toBe(true);
        expect(service.isRemovable(instruction)).toBe(false);
        expect(service.applicationCount(instruction)).toBe(2);
    });

    it('should expose the registered host state', () => {
        service.register(host);

        expect(service.isSelectable()).toBe(true);
        expect(service.isApplied(instruction)).toBe(true);
        expect(service.isApplied({ id: 2, credits: 0 } as GradingInstruction)).toBe(false);
    });

    it('should never treat an unsaved instruction without an id as applied', () => {
        service.register(host);

        expect(service.isApplied({ credits: 1 } as GradingInstruction)).toBe(false);
    });

    it('should delegate applying and un-applying to the host', () => {
        service.register(host);

        service.setApplied(instruction, true);
        expect(host.applyInstruction).toHaveBeenCalledWith(instruction);

        service.setApplied(instruction, false);
        expect(host.unapplyInstruction).toHaveBeenCalledWith(instruction);
    });

    it('should delegate single application changes to the host', () => {
        service.register(host);

        service.addApplication(instruction);
        expect(host.applyInstruction).toHaveBeenCalledWith(instruction);

        service.removeOneApplication(instruction);
        expect(host.unapplyOneInstruction).toHaveBeenCalledWith(instruction);
    });

    it('should ignore toggles while no host is registered', () => {
        service.setApplied(instruction, true);

        expect(host.applyInstruction).not.toHaveBeenCalled();
    });

    it('should only clear the registration for the host that is currently active', () => {
        const otherHost: GradingInstructionSelectionHost = {
            appliedInstructionIds: signal<ReadonlySet<number>>(new Set()),
            appliedInstructionCounts: signal<ReadonlyMap<number, number>>(new Map()),
            removableInstructionIds: signal<ReadonlySet<number>>(new Set()),
            applyInstruction: vi.fn(),
            unapplyOneInstruction: vi.fn(),
            unapplyInstruction: vi.fn(),
        };
        service.register(host);

        service.unregister(otherHost);
        expect(service.isSelectable()).toBe(true);

        service.unregister(host);
        expect(service.isSelectable()).toBe(false);
    });

    it('should clear an armed instruction explicitly and when a host registers', () => {
        service.armInstruction(instruction);
        expect(service.hasArmedInstruction()).toBe(true);

        service.clearArmedInstruction();
        expect(service.hasArmedInstruction()).toBe(false);

        service.armInstruction(instruction);
        service.register(host);
        expect(service.hasArmedInstruction()).toBe(false);
    });

    it('should reject a finite armed instruction after the registered host exhausts its usage limit', () => {
        const appliedCounts = signal<ReadonlyMap<number, number>>(new Map());
        service.register({ ...host, appliedInstructionCounts: appliedCounts });
        const limitedInstruction = { id: 1, credits: 2, usageCount: 1 } as GradingInstruction;

        service.armInstruction(limitedInstruction);
        appliedCounts.set(new Map([[limitedInstruction.id!, 1]]));

        expect(service.consumeArmedInstruction()).toBeUndefined();
        expect(service.hasArmedInstruction()).toBe(false);
    });
});
