import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { JhiAlertService } from 'ng-jhipster';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { Lecture } from 'app/entities/lecture.model';
import { LearningGoalFormData } from 'app/course/learning-goals/learning-goal-form/learning-goal-form.component';
import { EditLearningGoalComponent } from 'app/course/learning-goals/edit-learning-goal/edit-learning-goal.component';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { LectureService } from 'app/lecture/lecture.service';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe.ts';

chai.use(sinonChai);
const expect = chai.expect;

@Component({ selector: 'jhi-learning-goal-form', template: '' })
class LearningGoalFormStubComponent {
    @Input() formData: LearningGoalFormData;
    @Input() courseId: number;
    @Input() isEditMode = false;
    @Input()
    lecturesOfCourseWithLectureUnits: Lecture[] = [];
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
                MockProvider(JhiAlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        paramMap: Observable.of({
                            get: (key: string) => {
                                switch (key) {
                                    case 'learningGoalId':
                                        return 1;
                                }
                            },
                        }),
                        parent: {
                            parent: {
                                paramMap: Observable.of({
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

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        editLearningGoalComponentFixture.detectChanges();
        expect(editLearningGoalComponent).to.be.ok;
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

        const findByIdStub = sinon.stub(learningGoalService, 'findById').returns(of(learningGoalResponse));

        // mocking lecture service
        const lectureService = TestBed.inject(LectureService);
        const lectureOfResponse = new Lecture();
        lectureOfResponse.id = 1;
        lectureOfResponse.lectureUnits = [lectureUnit];

        const lecturesResponse: HttpResponse<Lecture[]> = new HttpResponse<Lecture[]>({
            body: [lectureOfResponse],
            status: 200,
        });

        const findAllByCourseStub = sinon.stub(lectureService, 'findAllByCourseId').returns(of(lecturesResponse));

        editLearningGoalComponentFixture.detectChanges();
        const learningGoalFormStubComponent: LearningGoalFormStubComponent = editLearningGoalComponentFixture.debugElement.query(By.directive(LearningGoalFormStubComponent))
            .componentInstance;
        expect(findByIdStub).to.have.been.calledOnce;
        expect(findAllByCourseStub).to.have.been.calledOnce;

        expect(editLearningGoalComponent.formData.title).to.equal(learningGoalOfResponse.title);
        expect(editLearningGoalComponent.formData.description).to.equal(learningGoalOfResponse.description);
        expect(editLearningGoalComponent.formData.connectedLectureUnits).to.deep.equal(learningGoalOfResponse.lectureUnits);
        expect(editLearningGoalComponent.lecturesWithLectureUnits).to.deep.equal([lectureOfResponse]);
        expect(learningGoalFormStubComponent.formData).to.deep.equal(editLearningGoalComponent.formData);
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
        const findByIdStub = sinon.stub(learningGoalService, 'findById').returns(of(findByIdResponse));
        sinon.stub(lectureService, 'findAllByCourseId').returns(
            of(
                new HttpResponse({
                    body: [new Lecture()],
                    status: 200,
                }),
            ),
        );
        editLearningGoalComponentFixture.detectChanges();
        expect(findByIdStub).to.have.been.calledOnce;
        expect(editLearningGoalComponent.learningGoal).to.deep.equal(learningGoalDatabase);

        const changedUnit: LearningGoal = {
            ...learningGoalDatabase,
            title: 'Changed',
        };

        const updateResponse: HttpResponse<LearningGoal> = new HttpResponse({
            body: changedUnit,
            status: 200,
        });
        const updatedStub = sinon.stub(learningGoalService, 'update').returns(of(updateResponse));
        const navigateSpy = sinon.spy(router, 'navigate');

        const learningGoalForm: LearningGoalFormStubComponent = editLearningGoalComponentFixture.debugElement.query(By.directive(LearningGoalFormStubComponent)).componentInstance;
        learningGoalForm.formSubmitted.emit({
            title: changedUnit.title,
            description: changedUnit.description,
            connectedLectureUnits: changedUnit.lectureUnits,
        });

        expect(updatedStub).to.have.been.calledOnce;
        expect(navigateSpy).to.have.been.calledOnce;
    });
});
