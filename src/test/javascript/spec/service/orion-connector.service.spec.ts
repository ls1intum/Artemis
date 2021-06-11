import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../test.module';
import { SinonSpy, spy } from 'sinon';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { MockOrionConnectorService } from '../helpers/mocks/service/mock-orion-connector.service';
import { TestBed } from '@angular/core/testing';
import { ExerciseAssessmentDashboardComponent } from 'app/exercises/shared/dashboards/tutor/exercise-assessment-dashboard.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ArtemisExerciseAssessmentDashboardModule } from 'app/exercises/shared/dashboards/tutor/exercise-assessment-dashboard.module';
import { TranslateModule } from '@ngx-translate/core';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';

chai.use(sinonChai);
const expect = chai.expect;

describe('OrionConnectorService', () => {
    let assessExerciseSpy: SinonSpy;
    let downloadSubmissionSpy: SinonSpy;
    let editExerciseSpy: SinonSpy;
    let buildAndTestLocallySpy: SinonSpy;
    let importParticipationSpy: SinonSpy;

    let exerciseAssessmentDashboardComponent: ExerciseAssessmentDashboardComponent;
    const programmingExercise = {
        id: 16,
        type: ExerciseType.PROGRAMMING,
    } as ProgrammingExercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisExerciseAssessmentDashboardModule,
                TranslateModule.forRoot(),],
            providers: [
                { provide: OrionConnectorService, useClass: MockOrionConnectorService },
                ExerciseAssessmentDashboardComponent,
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        });

        const orionConnectorService = TestBed.inject(OrionConnectorService);
        exerciseAssessmentDashboardComponent = TestBed.inject(ExerciseAssessmentDashboardComponent);

        downloadSubmissionSpy = spy(orionConnectorService, 'downloadSubmission');
        assessExerciseSpy = spy(orionConnectorService, 'assessExercise');
        editExerciseSpy = spy(orionConnectorService, 'editExercise');
        buildAndTestLocallySpy = spy(orionConnectorService, 'buildAndTestLocally');
        importParticipationSpy = spy(orionConnectorService, 'importParticipation');
    });
    afterEach(() => {
        downloadSubmissionSpy.restore();
        assessExerciseSpy.restore();
        editExerciseSpy.restore();
        buildAndTestLocallySpy.restore();
        importParticipationSpy.restore();
    });

    it('assessExercise should call connector', () => {
        exerciseAssessmentDashboardComponent.exercise = programmingExercise;
        exerciseAssessmentDashboardComponent.openAssessmentInOrion();

        expect(assessExerciseSpy).to.have.been.calledOnceWithExactly(programmingExercise);
    });
});
