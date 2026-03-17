import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ResolveMemoriesConflictsModalComponent } from './resolve-memories-conflicts-modal.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockProvider } from 'ng-mocks';
import { IrisMemoriesHttpService } from 'app/iris/overview/services/iris-memories-http.service';
import { AlertService } from 'app/shared/service/alert.service';
import { of, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ResolveMemoriesConflictsModalComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ResolveMemoriesConflictsModalComponent>;
    let component: ResolveMemoriesConflictsModalComponent;
    let http: { deleteUserMemory: ReturnType<typeof vi.fn> };
    let alerts: { error: ReturnType<typeof vi.fn> };
    const activeModalMock = { close: vi.fn(), dismiss: vi.fn() } as unknown as NgbActiveModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ResolveMemoriesConflictsModalComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbActiveModal, useValue: activeModalMock },
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
        // Default input state
        component.details = {};
        component.conflictGroups = [];
    });

    afterEach(() => {
        vi.restoreAllMocks();
        activeModalMock.close = vi.fn();
        activeModalMock.dismiss = vi.fn();
    });

    it('initializes groups and index on ngOnInit', () => {
        component.conflictGroups = [['a', 'b'], ['c']];
        component.ngOnInit();
        expect(component.currentIndex()).toBe(0);
        expect(component.currentGroup()).toEqual(['a', 'b']);
    });

    it('navigates with next and prev within bounds', () => {
        component.conflictGroups = [['a'], ['b'], ['c']];
        component.ngOnInit();
        expect(component.currentIndex()).toBe(0);
        component.next();
        expect(component.currentIndex()).toBe(1);
        component.next();
        expect(component.currentIndex()).toBe(2);
        component.prev();
        expect(component.currentIndex()).toBe(1);
    });

    it('close() dismisses the modal', () => {
        component.close();
        expect(activeModalMock.dismiss).toHaveBeenCalled();
    });

    it('keep() deletes other memories, advances or closes when no groups remain', async () => {
        component.conflictGroups = [
            ['m1', 'm2'],
            ['m3', 'm4'],
        ];
        component.ngOnInit();
        // Keep m2 -> delete m1
        await component.keep(component.currentIndex(), 'm2');
        expect(http.deleteUserMemory).toHaveBeenCalledWith('m1');
        expect(component.groups()).toHaveLength(1);
        // Keep m3 -> delete m4; modal closes with deletedIds
        await component.keep(component.currentIndex(), 'm3');
        expect(http.deleteUserMemory).toHaveBeenCalledWith('m4');
        expect(activeModalMock.close).toHaveBeenCalled();
        const callArg = (activeModalMock.close as ReturnType<typeof vi.fn>).mock.calls[0][0] as string[];
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
        component.conflictGroups = [['a', 'b']];
        component.ngOnInit();
        await component.keep(component.currentIndex(), 'a');
        expect(alerts.error).toHaveBeenCalledWith('artemisApp.iris.memories.error.deleteFailed');
        // Modal closes with only successful deletions (here none, since we kept 'a')
        expect(activeModalMock.close).toHaveBeenCalled();
        const arg = (activeModalMock.close as ReturnType<typeof vi.fn>).mock.calls[0][0] as string[];
        expect(arg).toEqual([]);
        expect(component.groups()).toHaveLength(0);
        expect(component.busy()).toBe(false);
    });
});
