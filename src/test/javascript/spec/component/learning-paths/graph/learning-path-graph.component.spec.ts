import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { LearningPathGraphComponent, LearningPathViewMode } from 'app/course/learning-paths/learning-path-graph/learning-path-graph.component';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { NgxLearningPathDTO, NgxLearningPathNode } from 'app/entities/competency/learning-path.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { MockModule } from 'ng-mocks';
import { NgxGraphModule } from '@swimlane/ngx-graph';

describe('LearningPathGraphComponent', () => {
    let fixture: ComponentFixture<LearningPathGraphComponent>;
    let comp: LearningPathGraphComponent;
    let learningPathService: LearningPathService;
    let getLearningPathNgxGraphStub: jest.SpyInstance;
    let getLearningPathNgxPathStub: jest.SpyInstance;
    const ngxGraph = { nodes: [{ id: '1' } as NgxLearningPathNode], edges: [] } as NgxLearningPathDTO;
    const ngxPath = { nodes: [{ id: '2' } as NgxLearningPathNode], edges: [] } as NgxLearningPathDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(NgxGraphModule)],
            declarations: [LearningPathGraphComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LearningPathGraphComponent);
                comp = fixture.componentInstance;
                learningPathService = TestBed.inject(LearningPathService);
                const ngxGraphResponse: HttpResponse<NgxLearningPathDTO> = new HttpResponse({
                    body: ngxGraph,
                    status: 200,
                });
                getLearningPathNgxGraphStub = jest.spyOn(learningPathService, 'getLearningPathNgxGraph').mockReturnValue(of(ngxGraphResponse));
                const ngxPathResponse: HttpResponse<NgxLearningPathDTO> = new HttpResponse({
                    body: ngxPath,
                    status: 200,
                });
                getLearningPathNgxPathStub = jest.spyOn(learningPathService, 'getLearningPathNgxPath').mockReturnValue(of(ngxPathResponse));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it.each([LearningPathViewMode.GRAPH, LearningPathViewMode.PATH])('should load learning path from service', (viewMode: LearningPathViewMode) => {
        comp.viewMode = viewMode;
        comp.learningPathId = 1;
        fixture.detectChanges();
        if (viewMode === LearningPathViewMode.GRAPH) {
            expect(getLearningPathNgxGraphStub).toHaveBeenCalledExactlyOnceWith(1);
            expect(getLearningPathNgxPathStub).not.toHaveBeenCalled();
        } else {
            expect(getLearningPathNgxGraphStub).not.toHaveBeenCalledOnce();
            expect(getLearningPathNgxPathStub).toHaveBeenCalledExactlyOnceWith(1);
        }
    });

    it('should update, center, and zoom to fit on resize', () => {
        const updateStub = jest.spyOn(comp.update$, 'next');
        const centerStub = jest.spyOn(comp.center$, 'next');
        const zoomToFitStub = jest.spyOn(comp.zoomToFit$, 'next');
        fixture.detectChanges();
        comp.onResize();
        expect(updateStub).toHaveBeenCalledExactlyOnceWith(true);
        expect(centerStub).toHaveBeenCalledExactlyOnceWith(true);
        expect(zoomToFitStub).toHaveBeenCalledExactlyOnceWith(true);
    });

    it('should zoom to fit and center on resize', () => {
        const zoomToFitStub = jest.spyOn(comp.zoomToFit$, 'next');
        const centerStub = jest.spyOn(comp.center$, 'next');
        fixture.detectChanges();
        comp.onCenterView();
        expect(zoomToFitStub).toHaveBeenCalledExactlyOnceWith(true);
        expect(centerStub).toHaveBeenCalledExactlyOnceWith(true);
    });

    it('should change view mode and load data if necessary', () => {
        comp.viewMode = LearningPathViewMode.GRAPH;
        comp.learningPathId = 1;
        fixture.detectChanges();
        expect(getLearningPathNgxGraphStub).toHaveBeenCalledExactlyOnceWith(1);
        expect(getLearningPathNgxPathStub).not.toHaveBeenCalled();
        comp.changeViewMode();
        expect(comp.viewMode).toEqual(LearningPathViewMode.PATH);
        expect(getLearningPathNgxPathStub).toHaveBeenCalledExactlyOnceWith(1);
        comp.changeViewMode();
        expect(comp.viewMode).toEqual(LearningPathViewMode.GRAPH);
        // make sure stub was not called again
        expect(getLearningPathNgxGraphStub).toHaveBeenCalledOnce();
    });
});
