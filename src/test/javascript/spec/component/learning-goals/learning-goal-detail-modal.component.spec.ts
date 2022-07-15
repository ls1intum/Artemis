import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { LearningGoalDetailModalComponent } from 'app/course/learning-goals/learning-goal-detail-modal/learning-goal-detail-modal.component';
import { IndividualLearningGoalProgress, IndividualLectureUnitProgress } from 'app/course/learning-goals/learning-goal-individual-progress-dtos.model';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortService } from 'app/shared/service/sort.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';

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
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
                MockDirective(SortDirective),
                LearningGoalDetailModalComponent,
                MockDirective(SortByDirective),
                MockComponent(FaIconComponent),
            ],
            providers: [
                MockProvider(SortService),
                {
                    provide: NgbActiveModal,
                    useValue: activeModalStub,
                },
                MockProvider(LectureUnitService),
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                learningGoalDetailModalComponentFixture = TestBed.createComponent(LearningGoalDetailModalComponent);
                learningGoalDetailModalComponent = learningGoalDetailModalComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        learningGoalDetailModalComponentFixture.detectChanges();
        expect(learningGoalDetailModalComponent).toBeDefined();
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
        const sortByPropertySpy = jest.spyOn(sortService, 'sortByProperty');
        learningGoalDetailModalComponent.sortConnectedLectureUnits();
        expect(sortByPropertySpy).toHaveBeenCalledOnce();
    }));
});
