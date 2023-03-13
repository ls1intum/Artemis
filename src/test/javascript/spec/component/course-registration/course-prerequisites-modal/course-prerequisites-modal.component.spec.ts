import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { of } from 'rxjs';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { CoursePrerequisitesModalComponent } from 'app/overview/course-registration/course-registration-prerequisites-modal/course-prerequisites-modal.component';
import { AlertService } from 'app/core/util/alert.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { LearningGoalCardStubComponent } from '../../learning-goals/learning-goal-card-stub.component';

describe('CoursePrerequisitesModal', () => {
    let coursePrerequisitesModalComponentFixture: ComponentFixture<CoursePrerequisitesModalComponent>;
    let coursePrerequisitesModalComponent: CoursePrerequisitesModalComponent;
    let learningGoalService: LearningGoalService;

    const activeModalStub = {
        close: () => {},
        dismiss: () => {},
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [CoursePrerequisitesModalComponent, LearningGoalCardStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(AlertService),
                MockProvider(LearningGoalService),
                {
                    provide: NgbActiveModal,
                    useValue: activeModalStub,
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                coursePrerequisitesModalComponentFixture = TestBed.createComponent(CoursePrerequisitesModalComponent);
                coursePrerequisitesModalComponent = coursePrerequisitesModalComponentFixture.componentInstance;
                coursePrerequisitesModalComponentFixture.componentInstance.courseId = 1;
                learningGoalService = TestBed.inject(LearningGoalService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load prerequisites and display a card for each of them', () => {
        const prerequisitesOfCourseResponse: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: [new LearningGoal(), new LearningGoal()],
            status: 200,
        });

        const getAllPrerequisitesForCourseSpy = jest.spyOn(learningGoalService, 'getAllPrerequisitesForCourse').mockReturnValue(of(prerequisitesOfCourseResponse));

        coursePrerequisitesModalComponentFixture.detectChanges();

        const learningGoalCards = coursePrerequisitesModalComponentFixture.debugElement.queryAll(By.directive(LearningGoalCardStubComponent));
        expect(learningGoalCards).toHaveLength(2);
        expect(getAllPrerequisitesForCourseSpy).toHaveBeenCalledOnce();
        expect(coursePrerequisitesModalComponent.prerequisites).toHaveLength(2);
    });

    it('should close modal when cleared', () => {
        const dismissActiveModal = jest.spyOn(activeModalStub, 'dismiss');
        coursePrerequisitesModalComponent.clear();
        expect(dismissActiveModal).toHaveBeenCalledOnce();
    });
});
