import { HttpClientModule, HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { EntityResponseType } from 'app/complaints/complaint.service';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExerciseGroupUpdateComponent } from 'app/exam/manage/exercise-groups/exercise-group-update.component';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as chai from 'chai';
import { AlertService } from 'app/core/util/alert.service';
import { MockComponent } from 'ng-mocks';
import { MockPipe } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of, throwError } from 'rxjs';
import * as sinon from 'sinon';
import { SinonStub, stub } from 'sinon';
import sinonChai from 'sinon-chai';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ExerciseGroupUpdateComponent', () => {
    const course = { id: 456 } as Course;
    const exam: Exam = new Exam();
    const exerciseGroup: ExerciseGroup = new ExerciseGroup();
    exam.course = course;
    exam.id = 123;
    exerciseGroup.id = 1;
    exerciseGroup.exam = exam;

    let component: ExerciseGroupUpdateComponent;
    let fixture: ComponentFixture<ExerciseGroupUpdateComponent>;
    let service: ExerciseGroupService;
    const mockRouter = new MockRouter();
    let alertServiceStub: SinonStub;
    let alertService: AlertService;

    const data = of({ exam, exerciseGroup });
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id, examId: exam.id }) }, data } as any as ActivatedRoute;
    const navigateSpy = sinon.spy(mockRouter, 'navigate');

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientModule, FormsModule],
            declarations: [ExerciseGroupUpdateComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent), MockComponent(AlertErrorComponent)],
            providers: [
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useValue: mockRouter },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseGroupUpdateComponent);
        component = fixture.componentInstance;
        service = TestBed.inject(ExerciseGroupService);
        alertService = TestBed.inject(AlertService);
    });

    // Always initialized and bind before tests
    beforeEach(fakeAsync(() => {
        fixture.detectChanges();
        tick();
        expect(component).to.be.ok;
        expect(component.exam).to.deep.equal(exam);
        expect(component.exerciseGroup).to.deep.equal(exerciseGroup);
    }));

    afterEach(function () {
        sinon.restore();
    });

    it('Should save exercise group', fakeAsync(() => {
        const responseFakeExerciseGroup = { body: exerciseGroup } as EntityResponseType;
        sinon.replace(service, 'update', sinon.fake.returns(of(responseFakeExerciseGroup)));

        component.save();

        expect(component.isSaving).to.be.false;
        expect(navigateSpy).to.have.been.calledWith(['course-management', course.id, 'exams', route.snapshot.paramMap.get('examId'), 'exercise-groups']);
        flush();
    }));

    it('Should save exercise group without ID', fakeAsync(() => {
        component.exerciseGroup.id = undefined;

        const responseFakeExerciseGroup = { body: exerciseGroup } as EntityResponseType;
        sinon.replace(service, 'create', sinon.fake.returns(of(responseFakeExerciseGroup)));

        component.save();

        expect(component.isSaving).to.be.false;
        expect(component.exam).to.deep.equal(exam);
        expect(navigateSpy).to.have.been.calledWith(['course-management', course.id, 'exams', route.snapshot.paramMap.get('examId'), 'exercise-groups']);
        flush();
    }));

    it('Should fail while saving with ErrorResponse', fakeAsync(() => {
        alertServiceStub = stub(alertService, 'error');
        const error = { status: 404 };
        component.exerciseGroup.id = undefined;

        sinon.replace(service, 'create', sinon.fake.returns(throwError(new HttpErrorResponse(error))));

        component.save();

        expect(component.isSaving).to.be.false;
        expect(component.exam).to.deep.equal(exam);
        expect(alertServiceStub).to.have.been.calledOnce;
        flush();
    }));
});
