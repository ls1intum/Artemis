import { describe, it } from 'vitest';

/**
 * Vitest specs for the navbar job tray (plan Sections 5.4 "State handling" and 10 "Client tests").
 * Use vi.spyOn()/vi.fn(); query by data-testid / element id, not CSS classes.
 */
describe('VariantGenerationTrayComponent', () => {
    // TODO (Sonnet): Implement per plan Section 10:
    // - spinner ring + count badge appear when a job starts (push a running job into the mocked
    // ExerciseVariantGenerationService.jobs signal);
    // - tray/job list survives wizard close (tray state lives in the service, not the wizard);
    // - a finished COMPLETED entry renders the variant title with a type-aware deep link to the editor;
    // - DRAFT_WITH_WARNINGS renders the warning badge + link; FAILED renders the failure phase; CANCELLED has no link;
    // - clicking an entry triggers the reopen-modal-in-monitor-mode path (spy on the shared open mechanism);
    // - cancel action shows the confirmation dialog and, on confirm, calls cancelJob and the entry transitions to
    // CANCELLED when the mocked CANCELLED event arrives.
    it.todo('TODO (Sonnet): implement tray specs (plan Section 10)');
});
