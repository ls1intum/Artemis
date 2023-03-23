import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { CreateLearningGoalComponent } from 'app/course/learning-goals/create-learning-goal/create-learning-goal.component';
import { LearningGoalFormData } from 'app/course/learning-goals/learning-goal-form/learning-goal-form.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { LectureService } from 'app/lecture/lecture.service';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { HttpResponse } from '@angular/common/http';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { By } from '@angular/platform-browser';
import { LearningGoalFormStubComponent } from './learning-goal-form-stub.component';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';

describe('CreateLearningGoal', () => {
    let createLearningGoalComponentFixture: ComponentFixture<CreateLearningGoalComponent>;
    let createLearningGoalComponent: CreateLearningGoalComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [LearningGoalFormStubComponent, CreateLearningGoalComponent, MockPipe(ArtemisTranslatePipe), MockComponent(DocumentationButtonComponent)],
            providers: [
                MockProvider(LearningGoalService),
                MockProvider(LectureService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            parent: {
                                paramMap: of({
                                    get: (key: string) => {
                                        switch (key) {
                                            case 'courseId':
                                                return 1;
                                        }
                                    },
                                }),
                            },
                        },
                    },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                createLearningGoalComponentFixture = TestBed.createComponent(CreateLearningGoalComponent);
                createLearningGoalComponent = createLearningGoalComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        createLearningGoalComponentFixture.detectChanges();
        expect(createLearningGoalComponent).toBeDefined();
    });

    it('should send POST request upon form submission and navigate', () => {
        const router: Router = TestBed.inject(Router);
        const learningGoalService = TestBed.inject(LearningGoalService);

        const textUnit: TextUnit = new TextUnit();
        textUnit.id = 1;
        const formData: LearningGoalFormData = {
            title: 'Test',
            description: 'Lorem Ipsum',
            connectedLectureUnits: [textUnit],
        };

        const response: HttpResponse<LearningGoal> = new HttpResponse({
            body: new LearningGoal(),
            status: 201,
        });

        const createSpy = jest.spyOn(learningGoalService, 'create').mockReturnValue(of(response));
        const navigateSpy = jest.spyOn(router, 'navigate');

        createLearningGoalComponentFixture.detectChanges();

        const learningGoalForm: LearningGoalFormStubComponent = createLearningGoalComponentFixture.debugElement.query(
            By.directive(LearningGoalFormStubComponent),
        ).componentInstance;
        learningGoalForm.formSubmitted.emit(formData);

        return createLearningGoalComponentFixture.whenStable().then(() => {
            const learningGoal = new LearningGoal();
            learningGoal.title = formData.title;
            learningGoal.description = formData.description;
            learningGoal.lectureUnits = formData.connectedLectureUnits;

            expect(createSpy).toHaveBeenCalledWith(learningGoal, 1);
            expect(createSpy).toHaveBeenCalledOnce();
            expect(navigateSpy).toHaveBeenCalledOnce();
        });
    });
});
