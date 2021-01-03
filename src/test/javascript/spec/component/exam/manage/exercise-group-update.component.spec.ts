import { ComponentFixture, fakeAsync, TestBed, tick, flush } from '@angular/core/testing';
import { HttpClientModule, HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroupUpdateComponent } from 'app/exam/manage/exercise-groups/exercise-group-update.component';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { JhiAlertService } from 'ng-jhipster';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { EntityResponseType } from 'app/complaints/complaint.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { SinonStub, stub } from 'sinon';
import { MockComponent } from 'ng-mocks/dist/lib/mock-component/mock-component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockPipe } from 'ng-mocks/dist/lib/mock-pipe/mock-pipe';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { FormsModule } from '@angular/forms';

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
    let alertService: JhiAlertService;

    const data = of({ exam, exerciseGroup });
    const route = ({ snapshot: { paramMap: convertToParamMap({ courseId: course.id, examId: exam.id }) }, data } as any) as ActivatedRoute;
    const navigateSpy = sinon.spy(mockRouter, 'navigate');

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientModule, FormsModule],
            declarations: [ExerciseGroupUpdateComponent, MockPipe(TranslatePipe), MockComponent(FaIconComponent), MockComponent(AlertErrorComponent)],
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
        alertService = TestBed.inject(JhiAlertService);
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
