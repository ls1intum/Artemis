import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { TranslateService } from '@ngx-translate/core';
import { ExerciseScoresExportButtonComponent } from 'app/exercises/shared/exercise-scores/exercise-scores-export-button.component';
import { ArtemisTestModule } from '../../test.module';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { Result } from 'app/entities/result.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('ExerciseScoresExportButtonComponent', () => {
    let component: ExerciseScoresExportButtonComponent;
    let fixture: ComponentFixture<ExerciseScoresExportButtonComponent>;
    let resultService: ResultService;

    const course1 = new Course();
    course1.id = 1;

    const exerciseGroup1 = new ExerciseGroup();
    exerciseGroup1.id = 1;

    const exercise1 = new ProgrammingExercise(course1, exerciseGroup1);
    exercise1.id = 1;

    const exercise2 = new ProgrammingExercise(course1, exerciseGroup1);
    exercise2.id = 2;

    const participation1 = new StudentParticipation();
    participation1.results = [];

    const participation2 = new StudentParticipation();
    participation2.results = [];

    const result1 = new Result();
    result1.id = 1;
    result1.score = 1;
    result1.participation = participation1;

    const result2 = new Result();
    result2.id = 2;
    result2.score = 2;
    result2.participation = participation2;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ExerciseScoresExportButtonComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseScoresExportButtonComponent);
        component = fixture.componentInstance;
        resultService = TestBed.inject(ResultService);
    });

    afterEach(async () => {
        sinon.restore();
    });

    it('should export results for one exercise', () => {
        // GIVEN
        const getResultsStub = sinon.stub(resultService, 'getResults').returns(of(new HttpResponse({ body: [result1, result2] })));
        component.exercise = exercise1;

        // WHEN
        component.exportResults();
        fixture.detectChanges();

        // THEN
        expect(getResultsStub).to.have.been.called;
    });

    it('should export results for multiple exercise', () => {
        // GIVEN
        const getResultsStub = sinon.stub(resultService, 'getResults').returns(of(new HttpResponse({ body: [result1, result2] })));
        component.exercises = [exercise1, exercise2];

        // WHEN
        component.exportResults();
        fixture.detectChanges();

        // THEN
        expect(getResultsStub).to.have.been.calledTwice;
    });
});
