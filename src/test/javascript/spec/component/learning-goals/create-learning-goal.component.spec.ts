import { Component, EventEmitter, Input, Output } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { JhiAlertService } from 'ng-jhipster';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, of } from 'rxjs';
import { CreateLearningGoalComponent } from 'app/course/learning-goals/create-learning-goal/create-learning-goal.component';
import { LearningGoalFormData } from 'app/course/learning-goals/learning-goal-form/learning-goal-form.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe.ts';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { LectureService } from 'app/lecture/lecture.service';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { Lecture } from 'app/entities/lecture.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { HttpResponse } from '@angular/common/http';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { By } from '@angular/platform-browser';

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

describe('CreateLearningGoal', () => {
    let createLearningGoalComponentFixture: ComponentFixture<CreateLearningGoalComponent>;
    let createLearningGoalComponent: CreateLearningGoalComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [LearningGoalFormStubComponent, CreateLearningGoalComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(LearningGoalService),
                MockProvider(LectureService),
                MockProvider(JhiAlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
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
                createLearningGoalComponentFixture = TestBed.createComponent(CreateLearningGoalComponent);
                createLearningGoalComponent = createLearningGoalComponentFixture.componentInstance;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        createLearningGoalComponentFixture.detectChanges();
        expect(createLearningGoalComponent).to.be.ok;
    });
    it('should send POST request upon form submission and navigate', fakeAsync(() => {
        const router: Router = TestBed.inject(Router);
        const learningGoalService = TestBed.inject(LearningGoalService);

        const textUnit: TextUnit = new TextUnit();
        textUnit.id = 1;
        const formDate: LearningGoalFormData = {
            title: 'Test',
            description: 'Lorem Ipsum',
            connectedLectureUnits: [textUnit],
        };

        const response: HttpResponse<LearningGoal> = new HttpResponse({
            body: new LearningGoal(),
            status: 201,
        });

        const createStub = sinon.stub(learningGoalService, 'create').returns(of(response));
        const navigateSpy = sinon.spy(router, 'navigate');

        createLearningGoalComponentFixture.detectChanges();

        const learningGoalForm: LearningGoalFormStubComponent = createLearningGoalComponentFixture.debugElement.query(By.directive(LearningGoalFormStubComponent))
            .componentInstance;
        learningGoalForm.formSubmitted.emit(formDate);

        createLearningGoalComponentFixture.whenStable().then(() => {
            const learningGoalCallArgument: LearningGoal = createStub.getCall(0).args[0];
            const courseIdCallArgument: number = createStub.getCall(0).args[1];

            expect(learningGoalCallArgument.title).to.equal(formDate.title);
            expect(learningGoalCallArgument.description).to.equal(formDate.description);
            expect(learningGoalCallArgument.lectureUnits).to.equal(formDate.connectedLectureUnits);
            expect(courseIdCallArgument).to.equal(1);

            expect(createStub).to.have.been.calledOnce;
            expect(navigateSpy).to.have.been.calledOnce;
        });
    }));
});
