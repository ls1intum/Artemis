import { ArtemisTestModule } from '../../../test.module';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import sinonChai from 'sinon-chai';
import * as chai from 'chai';
import * as sinon from 'sinon';
import { of } from 'rxjs';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExampleSubmission } from 'app/entities/example-submission.model';
import { ExampleSubmissionsComponent } from 'app/exercises/shared/example-submission/example-submissions.component';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('Example Submission Component', () => {
    let component: ExampleSubmissionsComponent;
    let fixture: ComponentFixture<ExampleSubmissionsComponent>;
    let exampleSubmissionService: ExampleSubmissionService;

    const exampleSubmission1 = { id: 1 } as ExampleSubmission;
    const exampleSubmission2 = { id: 2 } as ExampleSubmission;

    const exercise: Exercise = {
        id: 1,
        type: ExerciseType.TEXT,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
        exampleSubmissions: [exampleSubmission1, exampleSubmission2],
    };

    const route = { data: of({ courseId: 1 }), children: [] } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ExampleSubmissionsComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(ExampleSubmissionsComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExampleSubmissionsComponent);
                component = fixture.componentInstance;
                exampleSubmissionService = TestBed.inject(ExampleSubmissionService);
            });
    });

    it('should delete an example submission', () => {
        // GIVEN
        component.exercise = exercise;
        sinon.replace(exampleSubmissionService, 'delete', sinon.fake.returns(of({})));

        // WHEN
        component.deleteExampleSubmission(0);

        // THEN
        expect(exampleSubmissionService.delete).to.have.been.calledOnce;
        expect(exercise.exampleSubmissions?.length).to.eq(1);
    });
});
