import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { MockPipe } from 'ng-mocks';
import { LearningPathLegendComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-legend.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NodeType } from 'app/entities/competency/learning-path.model';
import { By } from '@angular/platform-browser';
import { NgbTooltipMocksModule } from '../../../helpers/mocks/directive/ngbTooltipMocks.module';

describe('LearningPathLegendComponent', () => {
    let fixture: ComponentFixture<LearningPathLegendComponent>;
    let comp: LearningPathLegendComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockPipe(ArtemisTranslatePipe), NgbTooltipMocksModule],
            declarations: [LearningPathLegendComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LearningPathLegendComponent);
                comp = fixture.componentInstance;
            });
    });

    it('should initialize', () => {
        expect(comp).not.toBeNull();
    });

    it.each([NodeType.COMPETENCY_END, NodeType.MATCH_START, NodeType.MATCH_END, NodeType.LECTURE_UNIT, NodeType.EXERCISE])(
        'should only show label for present node type',
        (type: NodeType) => {
            comp.nodeTypes = new Set<NodeType>();
            comp.nodeTypes.add(type);
            fixture.detectChanges();

            const competencyEnd = fixture.debugElement.query(By.css('#competency-end'));
            if (type === NodeType.COMPETENCY_END) {
                expect(competencyEnd).toBeTruthy();
            } else {
                expect(competencyEnd).toBeNull();
            }

            const matchStart = fixture.debugElement.query(By.css('#match-start'));
            if (type === NodeType.MATCH_START) {
                expect(matchStart).toBeTruthy();
            } else {
                expect(matchStart).toBeNull();
            }

            const matchEnd = fixture.debugElement.query(By.css('#match-end'));
            if (type === NodeType.MATCH_END) {
                expect(matchEnd).toBeTruthy();
            } else {
                expect(matchEnd).toBeNull();
            }

            const learningObject = fixture.debugElement.query(By.css('#learning-object'));
            const completedLearningObject = fixture.debugElement.query(By.css('#completed-learning-object'));
            if (type === NodeType.LECTURE_UNIT || type === NodeType.EXERCISE) {
                expect(learningObject).toBeTruthy();
                expect(completedLearningObject).toBeTruthy();
            } else {
                expect(learningObject).toBeNull();
                expect(completedLearningObject).toBeNull();
            }
        },
    );
});
