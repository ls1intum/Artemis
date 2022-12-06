import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { SortService } from 'app/shared/service/sort.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterTestingModule } from '@angular/router/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { LearningGoalCourseDetailModalComponent } from 'app/course/learning-goals/learning-goal-course-detail-modal/learning-goal-course-detail-modal.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';

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
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
                MockDirective(SortDirective),
                MockDirective(SortByDirective),
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

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        learningGoalCourseDetailModalFixture.detectChanges();
        expect(learningGoalCourseDetailModal).toBeDefined();
    });
});
