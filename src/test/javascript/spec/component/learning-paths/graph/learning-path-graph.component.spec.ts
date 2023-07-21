import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { LearningPathGraphComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-graph.component';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';

describe('LearningPathGraphComponent', () => {
    let fixture: ComponentFixture<LearningPathGraphComponent>;
    let comp: LearningPathGraphComponent;
    let learningPathService: LearningPathService;
    let getNgxLearningPathStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [LearningPathGraphComponent],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LearningPathGraphComponent);
                comp = fixture.componentInstance;
                learningPathService = TestBed.inject(LearningPathService);
                getNgxLearningPathStub = jest.spyOn(learningPathService, 'getNgxLearningPath');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load learning path from service', () => {
        comp.learningPathId = 1;
        fixture.detectChanges();
        expect(getNgxLearningPathStub).toHaveBeenCalledOnce();
        expect(getNgxLearningPathStub).toHaveBeenCalledWith(1);
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
});
