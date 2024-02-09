import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { NgxLearningPathDTO, NgxLearningPathNode, NodeType } from 'app/entities/competency/learning-path.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { LearningPathComponent } from 'app/course/learning-paths/learning-path-graph/learning-path.component';
import { ExerciseEntry, LearningPathStorageService, LectureUnitEntry } from 'app/course/learning-paths/participate/learning-path-storage.service';
import { LearningPathNodeComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-node.component';

describe('LearningPathComponent', () => {
    let fixture: ComponentFixture<LearningPathComponent>;
    let comp: LearningPathComponent;
    let learningPathService: LearningPathService;
    let getLearningPathNgxGraphStub: jest.SpyInstance;
    let getLearningPathNgxPathStub: jest.SpyInstance;
    let learningPathStorageService: LearningPathStorageService;
    let getRecommendationsStub: jest.SpyInstance;
    const ngxPath = {
        nodes: [
            { id: '1', linkedResource: 1, type: NodeType.EXERCISE } as NgxLearningPathNode,
            { id: '2', linkedResource: 2, linkedResourceParent: 3, type: NodeType.LECTURE_UNIT } as NgxLearningPathNode,
        ],
        edges: [],
    } as NgxLearningPathDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockComponent(LearningPathNodeComponent), MockPipe(ArtemisTranslatePipe), MockDirective(NgbTooltip)],
            declarations: [LearningPathComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LearningPathComponent);
                comp = fixture.componentInstance;
                learningPathService = TestBed.inject(LearningPathService);
                getLearningPathNgxGraphStub = jest.spyOn(learningPathService, 'getLearningPathNgxGraph').mockImplementation();
                const ngxPathResponse: HttpResponse<NgxLearningPathDTO> = new HttpResponse({
                    body: ngxPath,
                    status: 200,
                });
                getLearningPathNgxPathStub = jest.spyOn(learningPathService, 'getLearningPathNgxPath').mockReturnValue(of(ngxPathResponse));
                learningPathStorageService = TestBed.inject(LearningPathStorageService);
                getRecommendationsStub = jest.spyOn(learningPathStorageService, 'getRecommendations').mockReturnValue([new ExerciseEntry(1), new LectureUnitEntry(3, 2)]);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load data on init', () => {
        comp.learningPathId = 1;
        fixture.detectChanges();
        expect(getLearningPathNgxGraphStub).not.toHaveBeenCalled();
        expect(getLearningPathNgxPathStub).toHaveBeenCalledExactlyOnceWith(1);
        expect(getRecommendationsStub).toHaveBeenCalledExactlyOnceWith(1);
        expect(comp.path).toEqual(ngxPath.nodes);
    });

    it.each([new ExerciseEntry(1), new LectureUnitEntry(3, 2)])('should highlight node', (entry) => {
        comp.learningPathId = 1;
        fixture.detectChanges();
        comp.highlightNode(entry);
        if (entry instanceof LectureUnitEntry) {
            expect(comp.highlightedNode).toEqual(ngxPath.nodes[1]);
        } else {
            expect(comp.highlightedNode).toEqual(ngxPath.nodes[0]);
        }
    });

    it('should clear highlighting', () => {
        comp.learningPathId = 1;
        comp.highlightedNode = new NgxLearningPathNode();
        fixture.detectChanges();
        comp.clearHighlighting();
        expect(comp.highlightedNode).toBeUndefined();
    });
});
