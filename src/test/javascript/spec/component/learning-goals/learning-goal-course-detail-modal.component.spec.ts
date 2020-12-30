import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { TranslatePipe } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { SortService } from 'app/shared/service/sort.service';
import { JhiSortByDirective, JhiSortDirective, JhiTranslateDirective } from 'ng-jhipster';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterTestingModule } from '@angular/router/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { LearningGoalCourseDetailModalComponent } from 'app/course/learning-goals/learning-goal-course-detail-modal/learning-goal-course-detail-modal.component';
import { CourseLearningGoalProgress, CourseLectureUnitProgress } from 'app/course/learning-goals/learning-goal-course-progress.dtos.model';

chai.use(sinonChai);
const expect = chai.expect;
describe('LearningGoalCourseDetailModalComponent', () => {
    let learningGoalCourseDetailModalFixture: ComponentFixture<LearningGoalCourseDetailModalComponent>;
    let learningGoalCourseDetailModal: LearningGoalCourseDetailModalComponent;

    const activeModalStub = {
        close: () => {},
        dismiss: () => {},
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([])],
            declarations: [
                LearningGoalCourseDetailModalComponent,
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
                learningGoalCourseDetailModalFixture = TestBed.createComponent(LearningGoalCourseDetailModalComponent);
                learningGoalCourseDetailModal = learningGoalCourseDetailModalFixture.componentInstance;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        learningGoalCourseDetailModalFixture.detectChanges();
        expect(learningGoalCourseDetailModal).to.be.ok;
    });

    it('should call sort service', fakeAsync(() => {
        const learningGoal = new LearningGoal();
        learningGoal.lectureUnits = [new TextUnit(), new VideoUnit()];
        const learningGoalCourseProgress = new CourseLearningGoalProgress();
        learningGoalCourseProgress.totalPointsAchievableByStudentsInLearningGoal = 10;
        learningGoalCourseProgress.averagePointsAchievedByStudentInLearningGoal = 5;
        learningGoalCourseProgress.progressInLectureUnits = [new CourseLectureUnitProgress(), new CourseLectureUnitProgress()];
        learningGoalCourseDetailModal.learningGoal = learningGoal;
        learningGoalCourseDetailModal.learningGoalCourseProgress = learningGoalCourseProgress;
        learningGoalCourseDetailModalFixture.detectChanges();
        const sortService = TestBed.inject(SortService);
        const sortByPropertySpy = sinon.spy(sortService, 'sortByProperty');
        learningGoalCourseDetailModal.sortConnectedLectureUnits();
        expect(sortByPropertySpy).to.have.been.calledOnce;
    }));
});
