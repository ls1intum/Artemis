import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { MockDirective } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { LearningPathNodeComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-node.component';
import { NgxLearningPathNode, NodeType } from 'app/entities/competency/learning-path.model';
import { StickyPopoverDirective } from 'app/shared/sticky-popover/sticky-popover.directive';

describe('LearningPathGraphNodeComponent', () => {
    let fixture: ComponentFixture<LearningPathNodeComponent>;
    let comp: LearningPathNodeComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [LearningPathNodeComponent, MockDirective(StickyPopoverDirective)],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LearningPathNodeComponent);
                comp = fixture.componentInstance;
            });
    });

    it.each([NodeType.EXERCISE, NodeType.LECTURE_UNIT])('should display correct icon for completed learning object', (type: NodeType) => {
        comp.node = { id: '1', type: type, completed: true } as NgxLearningPathNode;
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('.completed'))).toBeTruthy();
    });

    it.each([NodeType.EXERCISE, NodeType.LECTURE_UNIT])('should display correct icon for not completed learning object', (type: NodeType) => {
        comp.node = { id: '1', type: type, completed: false } as NgxLearningPathNode;
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('#learning-object'))).toBeTruthy();
    });

    it.each([NodeType.COMPETENCY_START, NodeType.COMPETENCY_END])('should display correct icon for competency node', (type: NodeType) => {
        comp.node = { id: '1', type: type } as NgxLearningPathNode;
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('#competency')).nativeElement).toBeTruthy();
    });

    it.each([NodeType.MATCH_START, NodeType.MATCH_END])('should display correct icon for match node', (type: NodeType) => {
        comp.node = { id: '1', type: type } as NgxLearningPathNode;
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('#match')).nativeElement).toBeTruthy();
    });
});
