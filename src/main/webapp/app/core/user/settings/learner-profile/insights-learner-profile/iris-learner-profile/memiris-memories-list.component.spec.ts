import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MemirisMemoriesListComponent } from './memiris-memories-list.component';
import { IrisMemoriesHttpService } from 'app/iris/overview/services/iris-memories-http.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('MemirisMemoriesListComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<MemirisMemoriesListComponent>;
    let component: MemirisMemoriesListComponent;
    let http: { getUserMemoryData: ReturnType<typeof vi.fn>; getUserMemory: ReturnType<typeof vi.fn>; deleteUserMemory: ReturnType<typeof vi.fn> };
    let alerts: { error: ReturnType<typeof vi.fn> };

    const mockMemories = [
        { id: 'm1', title: 'Memory 1', content: '', learnings: [], connections: [], slept_on: false, deleted: false },
        { id: 'm2', title: 'Memory 2', content: '', learnings: [], connections: [], slept_on: false, deleted: false },
    ];
    const mockLearns = [
        { id: 'l1', title: 'Learning 1', content: 'L1 content', reference: 'ref1', memories: ['m1'] },
        { id: 'l2', title: 'Learning 2', content: 'L2 content', memories: ['m1'] },
    ];
    const mockConns = [{ id: 'c1', connectionType: 'related', memories: ['m1', 'm2'], description: 'desc', weight: 0.7 }];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MemirisMemoriesListComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(IrisMemoriesHttpService, {
                    getUserMemoryData: vi.fn().mockReturnValue(of({ memories: mockMemories, learnings: mockLearns, connections: mockConns } as any)),
                    getUserMemory: vi.fn(),
                    deleteUserMemory: vi.fn().mockReturnValue(of(void 0)),
                }),
                MockProvider(AlertService, { error: vi.fn() }),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(MemirisMemoriesListComponent);
        component = fixture.componentInstance;
        http = TestBed.inject(IrisMemoriesHttpService) as any;
        alerts = TestBed.inject(AlertService) as any;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create and load memories on init', async () => {
        await component.loadMemories();
        expect(component.memories()).toHaveLength(2);
    });

    it('should toggle open and compute details with caching', async () => {
        await component.loadMemories();
        const mem = component.memories()[0];
        await component.toggleOpen(mem);
        expect(component.details()[mem.id]).toBeTruthy();
        await component.toggleOpen(mem); // close
        await component.toggleOpen(mem); // open again
        expect(http.getUserMemory).not.toHaveBeenCalled();
    });

    it('should set details data when opening a memory', async () => {
        await component.loadMemories();
        const mem = component.memories()[0];
        await component.toggleOpen(mem);
        const details = component.details()[mem.id]!;
        expect(details.learnings).toHaveLength(2);
        expect(details.connections).toHaveLength(1);
    });

    it('should provide details with/without connections depending on aggregated data', async () => {
        (http.getUserMemoryData as ReturnType<typeof vi.fn>).mockReturnValueOnce(of({ memories: mockMemories, learnings: mockLearns, connections: [] } as any));
        await component.loadMemories();
        await component.toggleOpen(component.memories()[0]);
        expect(component.details()['m1']!.connections).toHaveLength(0);
    });

    it('should delete a memory and update list silently', async () => {
        const dataSpy = vi.spyOn(http, 'getUserMemoryData');
        await component.loadMemories();
        await component.deleteMemory(component.memories()[0]);
        expect(http.deleteUserMemory).toHaveBeenCalledWith('m1');
        expect(dataSpy).toHaveBeenCalledTimes(1);
        expect(component.memories()).toHaveLength(1);
        expect(component.deleting()['m1']).toBe(false);
    });

    it('should show alerts on errors for list and delete', async () => {
        (http.getUserMemoryData as ReturnType<typeof vi.fn>).mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        await component.loadMemories();
        expect(alerts.error).toHaveBeenCalledWith('artemisApp.iris.memories.error.loadFailed');

        (http.deleteUserMemory as ReturnType<typeof vi.fn>).mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        await component.deleteMemory({ id: 'm1' } as any);
        expect(alerts.error).toHaveBeenCalledWith('artemisApp.iris.memories.error.deleteFailed');
    });
});
