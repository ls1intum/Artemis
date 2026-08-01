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
            applyInstruction: vi.fn(),
            unapplyInstruction: vi.fn(),
        };
    });

    it('should report nothing as selectable or applied without a registered host', () => {
        expect(service.isSelectable()).toBe(false);
        expect(service.appliedInstructionIds().size).toBe(0);
        expect(service.isApplied(instruction)).toBe(false);
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

    it('should ignore toggles while no host is registered', () => {
        service.setApplied(instruction, true);

        expect(host.applyInstruction).not.toHaveBeenCalled();
    });

    it('should only clear the registration for the host that is currently active', () => {
        const otherHost: GradingInstructionSelectionHost = {
            appliedInstructionIds: signal<ReadonlySet<number>>(new Set()),
            applyInstruction: vi.fn(),
            unapplyInstruction: vi.fn(),
        };
        service.register(host);

        service.unregister(otherHost);
        expect(service.isSelectable()).toBe(true);

        service.unregister(host);
        expect(service.isSelectable()).toBe(false);
    });
});
