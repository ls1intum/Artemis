import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { LearningPathGraphComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-graph.component';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { CompetencyProgressForLearningPathDTO, NgxLearningPathDTO, NgxLearningPathNode, NodeType } from 'app/entities/competency/learning-path.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { NgxGraphModule } from '@swimlane/ngx-graph';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { LearningPathLegendComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-legend.component';

describe('LearningPathGraphComponent', () => {
    let fixture: ComponentFixture<LearningPathGraphComponent>;
    let comp: LearningPathGraphComponent;
    let learningPathService: LearningPathService;
    let getCompetencyProgressForLearningPathStub: jest.SpyInstance;
    let getLearningPathNgxGraphStub: jest.SpyInstance;
    let getLearningPathNgxPathStub: jest.SpyInstance;
    const progressDTO = { competencyId: 1 } as CompetencyProgressForLearningPathDTO;
    const ngxGraph = {
        nodes: [
            { id: '1', linkedResource: 1, type: NodeType.EXERCISE } as NgxLearningPathNode,
            { id: '2', linkedResource: 2, linkedResourceParent: 3, type: NodeType.LECTURE_UNIT } as NgxLearningPathNode,
        ],
        edges: [],
    } as NgxLearningPathDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(NgxGraphModule), MockPipe(ArtemisTranslatePipe), MockDirective(NgbTooltip), MockComponent(LearningPathLegendComponent)],
            declarations: [LearningPathGraphComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LearningPathGraphComponent);
                comp = fixture.componentInstance;
                learningPathService = TestBed.inject(LearningPathService);
                const competencyProgressResponse: HttpResponse<CompetencyProgressForLearningPathDTO[]> = new HttpResponse({
                    body: [progressDTO],
                    status: 200,
                });
                getCompetencyProgressForLearningPathStub = jest.spyOn(learningPathService, 'getCompetencyProgressForLearningPath').mockReturnValue(of(competencyProgressResponse));
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

    it('should load progress and learning path from service', () => {
        comp.learningPathId = 1;
        fixture.detectChanges();
        expect(getCompetencyProgressForLearningPathStub).toHaveBeenCalledExactlyOnceWith(1);
        expect(getLearningPathNgxGraphStub).toHaveBeenCalledExactlyOnceWith(1);
        expect(getLearningPathNgxPathStub).not.toHaveBeenCalled();

        expect(comp.competencyProgress.get(1)).toEqual(progressDTO);
    });

    it('should update, center, and zoom to fit on resize', () => {
        const updateStub = jest.spyOn(comp.update$, 'next');
        const centerStub = jest.spyOn(comp.center$, 'next');
        const zoomToFitStub = jest.spyOn(comp.zoomToFit$, 'next');
        fixture.detectChanges();
        comp.onResize();
        expect(updateStub).toHaveBeenCalledExactlyOnceWith(true);
        expect(centerStub).toHaveBeenCalledExactlyOnceWith(true);
        expect(zoomToFitStub).toHaveBeenCalledExactlyOnceWith({ autoCenter: true });
    });

    it('should zoom to fit and center on resize', () => {
        const zoomToFitStub = jest.spyOn(comp.zoomToFit$, 'next');
        const centerStub = jest.spyOn(comp.center$, 'next');
        fixture.detectChanges();
        comp.onCenterView();
        expect(zoomToFitStub).toHaveBeenCalledExactlyOnceWith({ autoCenter: true });
        expect(centerStub).toHaveBeenCalledExactlyOnceWith(true);
    });
});
