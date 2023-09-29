import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { MockDirective } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { LearningPathGraphNodeComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-graph-node.component';
import { NgxLearningPathNode, NodeType } from 'app/entities/competency/learning-path.model';
import { StickyPopoverDirective } from 'app/shared/sticky-popover/sticky-popover.directive';

describe('LearningPathGraphNodeComponent', () => {
    let fixture: ComponentFixture<LearningPathGraphNodeComponent>;
    let comp: LearningPathGraphNodeComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [LearningPathGraphNodeComponent, MockDirective(StickyPopoverDirective)],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LearningPathGraphNodeComponent);
                comp = fixture.componentInstance;
            });
    });

    it.each([NodeType.EXERCISE, NodeType.LECTURE_UNIT])('should display correct icon for completed learning object', (type: NodeType) => {
        comp.node = { id: '1', type: type, completed: true } as NgxLearningPathNode;
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('#completed')).nativeElement).toBeTruthy();
    });

    it.each([NodeType.EXERCISE, NodeType.LECTURE_UNIT])('should display correct icon for not completed learning object', (type: NodeType) => {
        comp.node = { id: '1', type: type, completed: false } as NgxLearningPathNode;
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('#not-completed')).nativeElement).toBeTruthy();
    });

    it.each([NodeType.COMPETENCY_START, NodeType.COMPETENCY_END, NodeType.COMPETENCY_START, NodeType.COMPETENCY_END])(
        'should display correct icon for generic node',
        (type: NodeType) => {
            comp.node = { id: '1', type: type } as NgxLearningPathNode;
            fixture.detectChanges();
            expect(fixture.debugElement.query(By.css('#generic')).nativeElement).toBeTruthy();
        },
    );
});
