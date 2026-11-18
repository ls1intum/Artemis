import { vi } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CompetencyGraphModalComponent } from 'app/atlas/manage/competency-graph-modal/competency-graph-modal.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiDialogConfig, TumAetUiDialogService } from '@tumaet/ui-angular';
import { LearningPathApiService } from 'app/atlas/shared/services/learning-path-api.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { CompetencyGraphDTO, CompetencyGraphEdgeDTO, CompetencyGraphNodeDTO } from 'app/atlas/shared/entities/learning-path.model';
import { CompetencyGraphComponent } from 'app/atlas/manage/competency-graph/competency-graph.component';
import { MockComponent } from 'ng-mocks';

describe('CompetencyGraphModalComponent', () => {
    let component: CompetencyGraphModalComponent;
    let fixture: ComponentFixture<CompetencyGraphModalComponent>;
    let learningPathApiService: LearningPathApiService;
    let alertService: AlertService;
    let getCompetencyPathSpy: ReturnType<typeof vi.spyOn>;

    const learningPathId = 1;

    const competencyGraph = <CompetencyGraphDTO>{
        nodes: [
            {
                id: '1',
                label: 'Node 1',
            } as CompetencyGraphNodeDTO,
            {
                id: '2',
                label: 'Node 2',
            } as CompetencyGraphNodeDTO,
        ],
        edges: [
            {
                source: '1',
                target: '2',
            } as CompetencyGraphEdgeDTO,
        ],
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CompetencyGraphModalComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                {
                    provide: AlertService,
                    useClass: MockAlertService,
                },
                { provide: TumAetUiDialogConfig, useValue: new TumAetUiDialogConfig({ data: { learningPathId } }) },
            ],
        })
            .overrideComponent(CompetencyGraphModalComponent, {
                remove: { imports: [CompetencyGraphComponent] },
                add: { imports: [MockComponent(CompetencyGraphComponent)] },
            })
            .compileComponents();

        learningPathApiService = TestBed.inject(LearningPathApiService);
        alertService = TestBed.inject(AlertService);

        getCompetencyPathSpy = vi.spyOn(learningPathApiService, 'getLearningPathCompetencyGraph').mockReturnValue(Promise.resolve(competencyGraph));

        fixture = TestBed.createComponent(CompetencyGraphModalComponent);
        component = fixture.componentInstance;
    });

    it('should initialize', () => {
        expect(component).toBeDefined();
        expect(component.learningPathId()).toBe(learningPathId);
    });

    it('should load data', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(getCompetencyPathSpy).toHaveBeenCalledExactlyOnceWith(learningPathId);
        expect(component.competencyGraph()).toEqual(competencyGraph);
    });

    it('should show alert on failed data loading', async () => {
        getCompetencyPathSpy.mockReturnValue(Promise.reject());
        const errorSpy = vi.spyOn(alertService, 'error');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(errorSpy).toHaveBeenCalledOnce();
    });

    it('should set isLoading correctly', async () => {
        const isLoadingSpy = vi.spyOn(component.isLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should open the dialog with a translated title including the name', () => {
        const open = vi.fn();

        CompetencyGraphModalComponent.openCompetencyGraphModal({ open } as unknown as TumAetUiDialogService, learningPathId, 'Alice');

        expect(open).toHaveBeenCalledOnce();
        const options = open.mock.calls[0][1];
        expect(options.size).toBe('large');
        expect(options.data).toEqual({
            learningPathId,
            name: 'Alice',
            headerKey: 'artemisApp.learningPath.competencyGraphModal.titleWithName',
            headerParams: { name: 'Alice' },
        });
    });

    it('should open the dialog with the plain title without a name', () => {
        const open = vi.fn();

        CompetencyGraphModalComponent.openCompetencyGraphModal({ open } as unknown as TumAetUiDialogService, learningPathId, undefined);

        expect(open.mock.calls[0][1].data).toEqual({ learningPathId, name: undefined, headerKey: 'artemisApp.learningPath.competencyGraphModal.title' });
    });
});
