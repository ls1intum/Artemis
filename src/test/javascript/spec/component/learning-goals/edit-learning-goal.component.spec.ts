import { HttpResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { EditLearningGoalComponent } from 'app/course/learning-goals/edit-learning-goal/edit-learning-goal.component';
import { LearningGoalFormData } from 'app/course/learning-goals/learning-goal-form/learning-goal-form.component';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { Lecture } from 'app/entities/lecture.model';
import { LectureService } from 'app/lecture/lecture.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { MockRouter } from '../../helpers/mocks/mock-router';

@Component({ selector: 'jhi-learning-goal-form', template: '' })
class LearningGoalFormStubComponent {
    @Input() formData: LearningGoalFormData;
    @Input() courseId: number;
    @Input() isEditMode = false;
    @Input() lecturesOfCourseWithLectureUnits: Lecture[] = [];
    @Output() formSubmitted: EventEmitter<LearningGoalFormData> = new EventEmitter<LearningGoalFormData>();
}

describe('EditLearningGoalComponent', () => {
    let editLearningGoalComponentFixture: ComponentFixture<EditLearningGoalComponent>;
    let editLearningGoalComponent: EditLearningGoalComponent;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [LearningGoalFormStubComponent, EditLearningGoalComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(LectureService),
                MockProvider(LearningGoalService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        paramMap: of({
                            get: (key: string) => {
                                switch (key) {
                                    case 'learningGoalId':
                                        return 1;
                                }
                            },
                        }),
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
                editLearningGoalComponentFixture = TestBed.createComponent(EditLearningGoalComponent);
                editLearningGoalComponent = editLearningGoalComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        editLearningGoalComponentFixture.detectChanges();
        expect(editLearningGoalComponent).toBeDefined();
    });

    it('should set form data correctly', () => {
        // mocking learning goal service
        const learningGoalService = TestBed.inject(LearningGoalService);
        const lectureUnit = new TextUnit();
        lectureUnit.id = 1;

        const learningGoalOfResponse = new LearningGoal();
        learningGoalOfResponse.id = 1;
        learningGoalOfResponse.title = 'test';
        learningGoalOfResponse.description = 'lorem ipsum';
        learningGoalOfResponse.lectureUnits = [lectureUnit];

        const learningGoalResponse: HttpResponse<LearningGoal> = new HttpResponse({
            body: learningGoalOfResponse,
            status: 200,
        });

        const findByIdSpy = jest.spyOn(learningGoalService, 'findById').mockReturnValue(of(learningGoalResponse));

        // mocking lecture service
        const lectureService = TestBed.inject(LectureService);
        const lectureOfResponse = new Lecture();
        lectureOfResponse.id = 1;
        lectureOfResponse.lectureUnits = [lectureUnit];

        const lecturesResponse: HttpResponse<Lecture[]> = new HttpResponse<Lecture[]>({
            body: [lectureOfResponse],
            status: 200,
        });

        const findAllByCourseSpy = jest.spyOn(lectureService, 'findAllByCourseId').mockReturnValue(of(lecturesResponse));

        editLearningGoalComponentFixture.detectChanges();
        const learningGoalFormStubComponent: LearningGoalFormStubComponent = editLearningGoalComponentFixture.debugElement.query(
            By.directive(LearningGoalFormStubComponent),
        ).componentInstance;
        expect(findByIdSpy).toHaveBeenCalledOnce();
        expect(findAllByCourseSpy).toHaveBeenCalledOnce();

        expect(editLearningGoalComponent.formData.title).toEqual(learningGoalOfResponse.title);
        expect(editLearningGoalComponent.formData.description).toEqual(learningGoalOfResponse.description);
        expect(editLearningGoalComponent.formData.connectedLectureUnits).toEqual(learningGoalOfResponse.lectureUnits);
        expect(editLearningGoalComponent.lecturesWithLectureUnits).toEqual([lectureOfResponse]);
        expect(learningGoalFormStubComponent.formData).toEqual(editLearningGoalComponent.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        const router: Router = TestBed.inject(Router);
        const learningGoalService = TestBed.inject(LearningGoalService);
        const lectureService = TestBed.inject(LectureService);

        const textUnit = new TextUnit();
        textUnit.id = 1;
        const learningGoalDatabase: LearningGoal = new LearningGoal();
        learningGoalDatabase.id = 1;
        learningGoalDatabase.title = 'test';
        learningGoalDatabase.description = 'lorem ipsum';
        learningGoalDatabase.lectureUnits = [textUnit];

        const findByIdResponse: HttpResponse<LearningGoal> = new HttpResponse({
            body: learningGoalDatabase,
            status: 200,
        });
        const findByIdSpy = jest.spyOn(learningGoalService, 'findById').mockReturnValue(of(findByIdResponse));
        jest.spyOn(lectureService, 'findAllByCourseId').mockReturnValue(
            of(
                new HttpResponse({
                    body: [new Lecture()],
                    status: 200,
                }),
            ),
        );
        editLearningGoalComponentFixture.detectChanges();
        expect(findByIdSpy).toHaveBeenCalledOnce();
        expect(editLearningGoalComponent.learningGoal).toEqual(learningGoalDatabase);

        const changedUnit: LearningGoal = {
            ...learningGoalDatabase,
            title: 'Changed',
        };

        const updateResponse: HttpResponse<LearningGoal> = new HttpResponse({
            body: changedUnit,
            status: 200,
        });
        const updatedSpy = jest.spyOn(learningGoalService, 'update').mockReturnValue(of(updateResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        const learningGoalForm: LearningGoalFormStubComponent = editLearningGoalComponentFixture.debugElement.query(By.directive(LearningGoalFormStubComponent)).componentInstance;
        learningGoalForm.formSubmitted.emit({
            title: changedUnit.title,
            description: changedUnit.description,
            connectedLectureUnits: changedUnit.lectureUnits,
        });

        expect(updatedSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledOnce();
    });
});
