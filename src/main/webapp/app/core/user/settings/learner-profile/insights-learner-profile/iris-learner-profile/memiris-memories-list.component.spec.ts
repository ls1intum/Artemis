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
    let fixture: ComponentFixture<MemirisMemoriesListComponent>;
    let component: MemirisMemoriesListComponent;
    let http: jest.Mocked<IrisMemoriesHttpService>;
    let alerts: jest.Mocked<AlertService>;

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
                    getUserMemoryData: jest.fn().mockReturnValue(of({ memories: mockMemories, learnings: mockLearns, connections: mockConns } as any)),
                    getUserMemory: jest.fn(),
                    deleteUserMemory: jest.fn().mockReturnValue(of(void 0)),
                }),
                MockProvider(AlertService, { error: jest.fn() }),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(MemirisMemoriesListComponent);
        component = fixture.componentInstance;
        http = TestBed.inject(IrisMemoriesHttpService) as jest.Mocked<IrisMemoriesHttpService>;
        alerts = TestBed.inject(AlertService) as jest.Mocked<AlertService>;
    });

    afterEach(() => jest.restoreAllMocks());

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
        expect(details.learnings).toBeArrayOfSize(2);
        expect(details.connections).toBeArrayOfSize(1);
    });

    it('should provide details with/without connections depending on aggregated data', async () => {
        (http.getUserMemoryData as jest.Mock).mockReturnValueOnce(of({ memories: mockMemories, learnings: mockLearns, connections: [] } as any));
        await component.loadMemories();
        await component.toggleOpen(component.memories()[0]);
        expect(component.details()['m1']!.connections).toHaveLength(0);
    });

    it('should delete a memory and update list silently', async () => {
        const dataSpy = jest.spyOn(http, 'getUserMemoryData');
        await component.loadMemories();
        await component.deleteMemory(component.memories()[0]);
        expect(http.deleteUserMemory).toHaveBeenCalledWith('m1');
        expect(dataSpy).toHaveBeenCalledOnce();
        expect(component.memories()).toHaveLength(1);
        expect(component.deleting()['m1']).toBeFalse();
    });

    it('should show alerts on errors for list and delete', async () => {
        (http.getUserMemoryData as jest.Mock).mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        await component.loadMemories();
        expect(alerts.error).toHaveBeenCalledWith('artemisApp.iris.memories.error.loadFailed');

        (http.deleteUserMemory as jest.Mock).mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        await component.deleteMemory({ id: 'm1' } as any);
        expect(alerts.error).toHaveBeenCalledWith('artemisApp.iris.memories.error.deleteFailed');
    });
});
