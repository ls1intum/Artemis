import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ResolveMemoriesConflictsModalComponent } from './resolve-memories-conflicts-modal.component';
import { MockProvider } from 'ng-mocks';
import { IrisMemoriesHttpService } from 'app/iris/overview/services/iris-memories-http.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { of, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ResolveMemoriesConflictsModalComponent', () => {
    let fixture: ComponentFixture<ResolveMemoriesConflictsModalComponent>;
    let component: ResolveMemoriesConflictsModalComponent;
    let http: { deleteUserMemory: ReturnType<typeof vi.fn> };
    let alerts: { error: ReturnType<typeof vi.fn> };
    let resolved: ReturnType<typeof vi.fn<(ids: string[]) => void>>;
    let closed: ReturnType<typeof vi.fn<() => void>>;

    /** Sets the conflict groups the way the host does, through the input. */
    function setConflictGroups(groups: string[][]): void {
        fixture.componentRef.setInput('conflictGroups', groups);
        fixture.detectChanges();
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ResolveMemoriesConflictsModalComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(IrisMemoriesHttpService, {
                    deleteUserMemory: vi.fn().mockReturnValue(of(void 0)),
                }),
                MockProvider(AlertService, { error: vi.fn() }),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ResolveMemoriesConflictsModalComponent);
        component = fixture.componentInstance;
        http = TestBed.inject(IrisMemoriesHttpService) as any;
        alerts = TestBed.inject(AlertService) as any;
        resolved = vi.fn<(ids: string[]) => void>();
        closed = vi.fn<() => void>();
        component.resolved.subscribe(resolved);
        component.closed.subscribe(closed);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('takes its working copy from the conflict groups it is given', () => {
        setConflictGroups([['a', 'b'], ['c']]);
        expect(component.currentIndex()).toBe(0);
        expect(component.currentGroup()).toEqual(['a', 'b']);
    });

    it('navigates with next and prev within bounds', () => {
        setConflictGroups([['a'], ['b'], ['c']]);
        expect(component.currentIndex()).toBe(0);
        component.next();
        expect(component.currentIndex()).toBe(1);
        component.next();
        expect(component.currentIndex()).toBe(2);
        component.prev();
        expect(component.currentIndex()).toBe(1);
    });

    it('close() dismisses the modal without reporting deletions', () => {
        component.close();
        expect(closed).toHaveBeenCalled();
        expect(resolved).not.toHaveBeenCalled();
    });

    it('keep() deletes other memories, advances or closes when no groups remain', async () => {
        setConflictGroups([
            ['m1', 'm2'],
            ['m3', 'm4'],
        ]);
        // Keep m2 -> delete m1
        await component.keep(component.currentIndex(), 'm2');
        expect(http.deleteUserMemory).toHaveBeenCalledWith('m1');
        expect(component.groups()).toHaveLength(1);
        // Keep m3 -> delete m4; modal closes with deletedIds
        await component.keep(component.currentIndex(), 'm3');
        expect(http.deleteUserMemory).toHaveBeenCalledWith('m4');
        expect(resolved).toHaveBeenCalled();
        const callArg = (resolved as ReturnType<typeof vi.fn>).mock.calls[0][0] as string[];
        // Deleted ids should include m1 and m4
        expect(new Set(callArg)).toEqual(new Set(['m1', 'm4']));
        expect(component.busy()).toBe(false);
    });

    it('keep() handles deletion errors and reports them, still resolves groups', async () => {
        // Set up a failure for b
        (http.deleteUserMemory as ReturnType<typeof vi.fn>).mockImplementation((id: string) => {
            if (id === 'b') return throwError(() => new Error('fail'));
            return of(void 0);
        });
        setConflictGroups([['a', 'b']]);
        await component.keep(component.currentIndex(), 'a');
        expect(alerts.error).toHaveBeenCalledWith('artemisApp.iris.memories.error.deleteFailed');
        // Modal closes with only successful deletions (here none, since we kept 'a')
        expect(resolved).toHaveBeenCalled();
        const arg = (resolved as ReturnType<typeof vi.fn>).mock.calls[0][0] as string[];
        expect(arg).toEqual([]);
        expect(component.groups()).toHaveLength(0);
        expect(component.busy()).toBe(false);
    });
});
