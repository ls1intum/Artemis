import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ConsistencyCheckError, ErrorType } from 'app/programming/shared/entities/consistency-check-result.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertService } from 'app/shared/service/alert.service';
import { of } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbActiveModal, NgbHighlight } from '@ng-bootstrap/ng-bootstrap';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { ConsistencyCheckComponent } from 'app/programming/manage/consistency-check/consistency-check.component';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';

describe('ConsistencyCheckComponent', () => {
    let component: ConsistencyCheckComponent;
    let fixture: ComponentFixture<ConsistencyCheckComponent>;
    let service: ConsistencyCheckService;

    const course = { id: 123, exercises: [] } as Course;
    const programmingExercise = new ProgrammingExercise(course, undefined);
    programmingExercise.id = 456;
    const programmingExercise2 = new ProgrammingExercise(course, undefined);
    programmingExercise.id = 567;
    const error1 = new ConsistencyCheckError();
    error1.programmingExercise = programmingExercise;
    error1.type = ErrorType.TEMPLATE_BUILD_PLAN_MISSING;
    const error2 = new ConsistencyCheckError();
    error2.programmingExercise = programmingExercise;
    error2.type = ErrorType.SOLUTION_BUILD_PLAN_MISSING;

    const consistencyErrors = [error1, error2];
    const programmingExercises = [programmingExercise, programmingExercise2];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockComponent(NgbHighlight)],
            declarations: [ConsistencyCheckComponent, MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe), MockRouterLinkDirective],
            providers: [
                MockProvider(TranslateService),
                MockProvider(AlertService),
                MockProvider(ConsistencyCheckService),
                MockProvider(NgbActiveModal),
                MockProvider(ConsistencyCheckService),
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ConsistencyCheckComponent);
                component = fixture.componentInstance;
                service = TestBed.inject(ConsistencyCheckService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should call checks for single programming exercise', () => {
        // GIVEN
        const checkConsistencyForProgrammingExerciseStub = jest.spyOn(service, 'checkConsistencyForProgrammingExercise').mockReturnValue(of(consistencyErrors));

        // WHEN
        component.exercisesToCheck = Array.of(programmingExercise);
        fixture.changeDetectorRef.detectChanges();

        // THEN
        expect(checkConsistencyForProgrammingExerciseStub).toHaveBeenCalledOnce();
        expect(component.inconsistencies).toEqual(consistencyErrors);
    });

    it('should call checks for multiple programming exercises', () => {
        // GIVEN
        const checkConsistencyForProgrammingExerciseStub = jest.spyOn(service, 'checkConsistencyForProgrammingExercise').mockReturnValue(of(consistencyErrors));

        // WHEN
        component.exercisesToCheck = programmingExercises;
        fixture.changeDetectorRef.detectChanges();

        // THEN
        expect(checkConsistencyForProgrammingExerciseStub).toHaveBeenCalledTimes(2);
        expect(component.inconsistencies).toEqual(consistencyErrors.concat(consistencyErrors));
    });
});
