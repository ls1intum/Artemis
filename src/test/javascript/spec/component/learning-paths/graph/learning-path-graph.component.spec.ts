import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { LearningPathGraphComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-graph.component';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';

describe('LearningPathGraphComponent', () => {
    let fixture: ComponentFixture<LearningPathGraphComponent>;
    let comp: LearningPathGraphComponent;
    let learningPathService: LearningPathService;
    let getLearningPathNgxGraphStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [LearningPathGraphComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LearningPathGraphComponent);
                comp = fixture.componentInstance;
                learningPathService = TestBed.inject(LearningPathService);
                getLearningPathNgxGraphStub = jest.spyOn(learningPathService, 'getLearningPathNgxGraph');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load learning path from service', () => {
        comp.learningPathId = 1;
        fixture.detectChanges();
        expect(getLearningPathNgxGraphStub).toHaveBeenCalledOnce();
        expect(getLearningPathNgxGraphStub).toHaveBeenCalledWith(1);
    });

    it('should update, center, and zoom to fit on resize', () => {
        const updateStub = jest.spyOn(comp.update$, 'next');
        const centerStub = jest.spyOn(comp.center$, 'next');
        const zoomToFitStub = jest.spyOn(comp.zoomToFit$, 'next');
        fixture.detectChanges();
        comp.onResize();
        expect(updateStub).toHaveBeenCalledOnce();
        expect(updateStub).toHaveBeenCalledWith(true);
        expect(centerStub).toHaveBeenCalledOnce();
        expect(centerStub).toHaveBeenCalledWith(true);
        expect(zoomToFitStub).toHaveBeenCalledOnce();
        expect(zoomToFitStub).toHaveBeenCalledWith(true);
    });

    it('should zoom to fit and center on resize', () => {
        const zoomToFitStub = jest.spyOn(comp.zoomToFit$, 'next');
        const centerStub = jest.spyOn(comp.center$, 'next');
        fixture.detectChanges();
        comp.onCenterView();
        expect(zoomToFitStub).toHaveBeenCalledOnce();
        expect(zoomToFitStub).toHaveBeenCalledWith(true);
        expect(centerStub).toHaveBeenCalledOnce();
        expect(centerStub).toHaveBeenCalledWith(true);
    });
});
