import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import * as chai from 'chai';

import { CheckType, ConsistencyCheckComponent } from 'app/shared/consistency-check/consistency-check.component';
import { ConsistencyCheckService } from 'app/shared/consistency-check/consistency-check.service';
import { ArtemisTestModule } from '../../test.module';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { JhiAlertService } from 'ng-jhipster';
import { ConsistencyCheckError, ErrorType } from 'app/entities/consistency-check-result.model';
import { Course } from 'app/entities/course.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { MockAlertService } from '../../helpers/mocks/service/mock-alert.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ConsistencyCheckComponent', () => {
    let component: ConsistencyCheckComponent;
    let fixture: ComponentFixture<ConsistencyCheckComponent>;
    let service: ConsistencyCheckService;

    const course = { id: 123 } as Course;
    const programmingExercise = new ProgrammingExercise(course, undefined);
    programmingExercise.id = 456;
    course.exercises?.push(programmingExercise);
    const error1 = new ConsistencyCheckError();
    error1.programmingExercise = programmingExercise;
    error1.type = ErrorType.TEMPLATE_BUILD_PLAN_MISSING;
    const error2 = new ConsistencyCheckError();
    error2.programmingExercise = programmingExercise;
    error2.type = ErrorType.SOLUTION_BUILD_PLAN_MISSING;

    const consistencyErrors = [error1, error2];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ConsistencyCheckComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: JhiAlertService, useClass: MockAlertService },
            ],
        })
            .overrideTemplate(ConsistencyCheckComponent, '')
            .compileComponents();
        fixture = TestBed.createComponent(ConsistencyCheckComponent);
        component = fixture.componentInstance;
        service = TestBed.inject(ConsistencyCheckService);
    });

    afterEach(async () => {
        sinon.restore();
    });

    it('should call checks for single programming exercise', () => {
        // GIVEN
        const checkConsistencyForProgrammingExercise = sinon.stub(service, 'checkConsistencyForProgrammingExercise').returns(of(consistencyErrors));

        // WHEN
        component.checkType = CheckType.PROGRAMMING_EXERCISE;
        component.id = programmingExercise.id!;
        fixture.detectChanges();

        // THEN
        expect(checkConsistencyForProgrammingExercise).to.have.been.called;
        expect(component.courseId).to.be.equal(programmingExercise.course!.id);
        expect(component.errors).to.be.equal(consistencyErrors);
    });

    it('should call checks for course', () => {
        // GIVEN
        const checkConsistencyForProgrammingExercise = sinon.stub(service, 'checkConsistencyForCourse').returns(of(consistencyErrors));

        // WHEN
        component.checkType = CheckType.COURSE;
        component.id = course.id!;
        fixture.detectChanges();

        // THEN
        expect(checkConsistencyForProgrammingExercise).to.have.been.called;
        expect(component.courseId).to.be.equal(course.id);
        expect(component.errors).to.be.equal(consistencyErrors);
    });
});
