import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { MockPipe } from 'ng-mocks';
import { LearningPathLegendComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-legend.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('LearningPathLegendComponent', () => {
    let fixture: ComponentFixture<LearningPathLegendComponent>;
    let comp: LearningPathLegendComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockPipe(ArtemisTranslatePipe)],
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
});
