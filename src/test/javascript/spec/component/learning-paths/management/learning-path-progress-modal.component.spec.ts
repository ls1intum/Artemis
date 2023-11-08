import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent } from 'ng-mocks';
import { LearningPathProgressModalComponent } from 'app/course/learning-paths/progress-modal/learning-path-progress-modal.component';
import { LearningPathGraphComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-graph.component';
import { By } from '@angular/platform-browser';
import { LearningPathProgressNavComponent } from 'app/course/learning-paths/progress-modal/learning-path-progress-nav.component';
import { LearningPathInformationDTO, NgxLearningPathNode, NodeType } from 'app/entities/competency/learning-path.model';
import { Router } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';

describe('LearningPathProgressModalComponent', () => {
    let fixture: ComponentFixture<LearningPathProgressModalComponent>;
    let comp: LearningPathProgressModalComponent;
    let activeModal: NgbActiveModal;
    let closeStub: jest.SpyInstance;
    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockComponent(LearningPathGraphComponent), MockComponent(LearningPathProgressNavComponent)],
            declarations: [LearningPathProgressModalComponent],
            providers: [{ provide: Router, useValue: router }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LearningPathProgressModalComponent);
                comp = fixture.componentInstance;
                activeModal = TestBed.inject(NgbActiveModal);
                closeStub = jest.spyOn(activeModal, 'close');
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        router.navigate.mockRestore();
    });

    it('should display learning path graph if learning path is present', () => {
        comp.courseId = 2;
        comp.learningPath = { id: 1 } as LearningPathInformationDTO;
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('.graph')).nativeElement).toBeTruthy();
    });

    it('should correctly close modal', () => {
        comp.close();
        expect(closeStub).toHaveBeenCalledOnce();
    });

    it.each([
        { id: '1', type: NodeType.COMPETENCY_START, linkedResource: 3 } as NgxLearningPathNode,
        { id: '1', type: NodeType.COMPETENCY_END, linkedResource: 3 } as NgxLearningPathNode,
    ])('should navigate on competency node clicked', (node) => {
        comp.courseId = 2;
        comp.learningPath = { id: 1 } as LearningPathInformationDTO;
        fixture.detectChanges();
        comp.onNodeClicked(node);
        expect(router.navigate).toHaveBeenCalledOnce();
        expect(router.navigate).toHaveBeenCalledWith(['courses', 2, 'competencies', 3]);
    });
});
