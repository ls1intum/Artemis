import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe.ts';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { LearningGoalDetailModalComponent } from 'app/course/learning-goals/learning-goal-detail-modal/learning-goal-detail-modal.component';
import { SortService } from 'app/shared/service/sort.service';
import { JhiSortByDirective, JhiSortDirective, JhiTranslateDirective } from 'ng-jhipster';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterTestingModule } from '@angular/router/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { IndividualLearningGoalProgress, IndividualLectureUnitProgress } from 'app/course/learning-goals/learning-goal-individual-progress-dtos.model';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';

chai.use(sinonChai);
const expect = chai.expect;
describe('LearningGoalDetailModalComponent', () => {
    let learningGoalDetailModalComponentFixture: ComponentFixture<LearningGoalDetailModalComponent>;
    let learningGoalDetailModalComponent: LearningGoalDetailModalComponent;

    const activeModalStub = {
        close: () => {},
        dismiss: () => {},
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([])],
            declarations: [
                LearningGoalDetailModalComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(JhiTranslateDirective),
                MockDirective(JhiSortDirective),
                MockDirective(JhiSortByDirective),
                MockComponent(FaIconComponent),
            ],
            providers: [
                MockProvider(LectureUnitService),
                MockProvider(SortService),
                {
                    provide: NgbActiveModal,
                    useValue: activeModalStub,
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                learningGoalDetailModalComponentFixture = TestBed.createComponent(LearningGoalDetailModalComponent);
                learningGoalDetailModalComponent = learningGoalDetailModalComponentFixture.componentInstance;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        learningGoalDetailModalComponentFixture.detectChanges();
        expect(learningGoalDetailModalComponent).to.be.ok;
    });

    it('should call sort service', fakeAsync(() => {
        const learningGoal = new LearningGoal();
        learningGoal.lectureUnits = [new TextUnit(), new VideoUnit()];
        const learningGoalProgress = new IndividualLearningGoalProgress();
        learningGoalProgress.progressInLectureUnits = [new IndividualLectureUnitProgress(), new IndividualLectureUnitProgress()];
        learningGoalDetailModalComponent.learningGoal = learningGoal;
        learningGoalDetailModalComponent.learningGoalProgress = learningGoalProgress;
        learningGoalDetailModalComponentFixture.detectChanges();
        const sortService = TestBed.inject(SortService);
        const sortByPropertySpy = sinon.spy(sortService, 'sortByProperty');
        learningGoalDetailModalComponent.sortConnectedLectureUnits();
        expect(sortByPropertySpy).to.have.been.calledOnce;
    }));
});
