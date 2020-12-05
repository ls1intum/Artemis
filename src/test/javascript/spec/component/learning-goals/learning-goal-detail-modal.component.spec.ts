import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { TranslatePipe } from '@ngx-translate/core';
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
import { LearningGoalProgress, LectureUnitProgress } from 'app/course/learning-goals/learning-goal-progress-dtos.model';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';

chai.use(sinonChai);
const expect = chai.expect;
describe('LearningGoalDetailModalComponent', () => {
    let learningGoalDetailModalComponentFixture: ComponentFixture<LearningGoalDetailModalComponent>;
    let learningGoalDetailModalComponent: LearningGoalDetailModalComponent;

    const activeModalStub = {
        close: (result?: any) => {},
        dismiss: (reason?: any) => {},
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([])],
            declarations: [
                LearningGoalDetailModalComponent,
                MockPipe(TranslatePipe),
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
        const learningGoalProgress = new LearningGoalProgress();
        learningGoalProgress.progressInLectureUnits = [new LectureUnitProgress(), new LectureUnitProgress()];
        learningGoalDetailModalComponent.learningGoal = learningGoal;
        learningGoalDetailModalComponent.learningGoalProgress = learningGoalProgress;
        learningGoalDetailModalComponentFixture.detectChanges();
        const sortService = TestBed.inject(SortService);
        const sortByPropertySpy = sinon.spy(sortService, 'sortByProperty');
        learningGoalDetailModalComponent.sortConnectedLectureUnits();
        learningGoalDetailModalComponent.sortConnectedLectureUnitsForCalculation();
        expect(sortByPropertySpy).to.have.been.calledTwice;
    }));
});
