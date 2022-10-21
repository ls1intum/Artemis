import { ComponentFixture, TestBed } from '@angular/core/testing';
import { faCheckCircle, faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { ExerciseType } from 'app/entities/exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Result } from 'app/entities/result.model';
import { Submission } from 'app/entities/submission.model';
import { ResultComponent, ResultTemplateStatus } from 'app/exercises/shared/result/result.component';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { cloneDeep } from 'lodash-es';
import { MockDirective, MockPipe } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';

describe('ResultComponent', () => {
    let fixture: ComponentFixture<ResultComponent>;
    let component: ResultComponent;

    const result: Result = { id: 0, participation: {}, submission: {} };
    const programmingExercise: ProgrammingExercise = {
        id: 1,
        type: ExerciseType.PROGRAMMING,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
    };
    const programmingParticipation: ProgrammingExerciseStudentParticipation = { id: 2, type: ParticipationType.PROGRAMMING, exercise: programmingExercise };

    const modelingExercise: ModelingExercise = {
        id: 3,
        type: ExerciseType.MODELING,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
    };
    const modelingParticipation: StudentParticipation = { id: 4, type: ParticipationType.STUDENT, exercise: modelingExercise };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ResultComponent, MockDirective(NgbTooltip), MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisTimeAgoPipe)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ResultComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        component.result = result;
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should set results for programming exercise', () => {
        const submission1: Submission = { id: 1 };
        const result1: Result = { id: 1, submission: submission1, score: 1 };
        const result2: Result = { id: 2 };
        const participation1 = cloneDeep(programmingParticipation);
        participation1.results = [result1, result2];
        component.participation = participation1;

        fixture.detectChanges();

        expect(component.result).toEqual(result1);
        expect(component.result!.participation).toEqual(participation1);
        expect(component.submission).toEqual(submission1);
        expect(component.textColorClass).toBe('text-secondary');
        expect(component.hasFeedback).toBeFalse();
        expect(component.resultIconClass).toEqual(faQuestionCircle);
        expect(component.resultString).toBe('artemisApp.result.resultStringProgramming (artemisApp.result.preliminary)');
    });

    it('should set results foo for modeling exercise', () => {
        const submission1: Submission = { id: 1 };
        const result1: Result = { id: 1, submission: submission1, score: 1 };
        const result2: Result = { id: 2 };
        const participation1 = cloneDeep(modelingParticipation);
        participation1.results = [result1, result2];
        component.participation = participation1;

        fixture.detectChanges();

        expect(component.result).toEqual(result1);
        expect(component.result!.participation).toEqual(participation1);
        expect(component.submission).toEqual(submission1);
        expect(component.textColorClass).toBe('text-danger');
        expect(component.hasFeedback).toBeFalse();
        expect(component.resultIconClass).toEqual(faCheckCircle);
        expect(component.resultString).toBe('artemisApp.result.resultStringNonProgramming');
        expect(component.templateStatus).toBe(ResultTemplateStatus.HAS_RESULT);
    });
});
