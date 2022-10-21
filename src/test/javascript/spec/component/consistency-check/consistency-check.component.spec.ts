import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbHighlight } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/util/alert.service';
import { ConsistencyCheckError, ErrorType } from 'app/entities/consistency-check-result.model';
import { Course } from 'app/entities/course.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ConsistencyCheckComponent } from 'app/shared/consistency-check/consistency-check.component';
import { ConsistencyCheckService } from 'app/shared/consistency-check/consistency-check.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { ArtemisTestModule } from '../../test.module';

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
            imports: [ArtemisTestModule],
            declarations: [ConsistencyCheckComponent, MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe), MockComponent(NgbHighlight), MockRouterLinkDirective],
            providers: [MockProvider(TranslateService), MockProvider(AlertService), MockProvider(ConsistencyCheckService)],
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
        fixture.detectChanges();

        // THEN
        expect(checkConsistencyForProgrammingExerciseStub).toHaveBeenCalledOnce();
        expect(component.inconsistencies).toEqual(consistencyErrors);
    });

    it('should call checks for multiple programming exercises', () => {
        // GIVEN
        const checkConsistencyForProgrammingExerciseStub = jest.spyOn(service, 'checkConsistencyForProgrammingExercise').mockReturnValue(of(consistencyErrors));

        // WHEN
        component.exercisesToCheck = programmingExercises;
        fixture.detectChanges();

        // THEN
        expect(checkConsistencyForProgrammingExerciseStub).toHaveBeenCalledTimes(2);
        expect(component.inconsistencies).toEqual(consistencyErrors.concat(consistencyErrors));
    });
});
