import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { LearningPathGraphComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-graph.component';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { NgxLearningPathDTO, NgxLearningPathNode, NodeType } from 'app/entities/competency/learning-path.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { NgxGraphModule } from '@swimlane/ngx-graph';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

describe('LearningPathGraphComponent', () => {
    let fixture: ComponentFixture<LearningPathGraphComponent>;
    let comp: LearningPathGraphComponent;
    let learningPathService: LearningPathService;
    let getLearningPathNgxGraphStub: jest.SpyInstance;
    let getLearningPathNgxPathStub: jest.SpyInstance;
    const ngxGraph = {
        nodes: [
            { id: '1', linkedResource: 1, type: NodeType.EXERCISE } as NgxLearningPathNode,
            { id: '2', linkedResource: 2, linkedResourceParent: 3, type: NodeType.LECTURE_UNIT } as NgxLearningPathNode,
        ],
        edges: [],
    } as NgxLearningPathDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(NgxGraphModule), MockPipe(ArtemisTranslatePipe), MockDirective(NgbTooltip)],
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
                getLearningPathNgxPathStub = jest.spyOn(learningPathService, 'getLearningPathNgxPath').mockImplementation();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load learning path from service', () => {
        comp.learningPathId = 1;
        fixture.detectChanges();
        expect(getLearningPathNgxGraphStub).toHaveBeenCalledExactlyOnceWith(1);
        expect(getLearningPathNgxPathStub).not.toHaveBeenCalled();
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
});
