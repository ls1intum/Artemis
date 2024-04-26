import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { Result } from 'app/entities/result.model';
import { ArtemisTestModule } from '../../../test.module';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { cloneDeep } from 'lodash-es';
import { Submission } from 'app/entities/submission.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { faQuestionCircle, faTimesCircle } from '@fortawesome/free-regular-svg-icons';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ResultTemplateStatus } from 'app/exercises/shared/result/result.utils';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import dayjs from 'dayjs/esm';
import { MIN_SCORE_GREEN, MIN_SCORE_ORANGE } from 'app/app.constants';

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
            imports: [ArtemisTestModule, MockDirective(NgbTooltip)],
            declarations: [ResultComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisTimeAgoPipe), MockPipe(ArtemisDatePipe)],
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
        expect(component.resultIconClass).toEqual(faQuestionCircle);
        expect(component.resultString).toBe('artemisApp.result.resultString.programmingShort (artemisApp.result.preliminary)');
    });

    it('should set results for modeling exercise', () => {
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
        expect(component.resultIconClass).toEqual(faTimesCircle);
        expect(component.resultString).toBe('artemisApp.result.resultString.short');
        expect(component.templateStatus).toBe(ResultTemplateStatus.HAS_RESULT);
    });

    it.each([
        // never show icon in long format, the text already contains the relevant information
        { short: false, score: MIN_SCORE_ORANGE - 3, codeIssues: 1, iconShown: false },
        { short: false, score: MIN_SCORE_ORANGE, codeIssues: 1, iconShown: false },
        { short: false, score: MIN_SCORE_GREEN, codeIssues: 2, iconShown: false },
        // show independent of score
        { short: true, score: MIN_SCORE_ORANGE - 3, codeIssues: 1, iconShown: true },
        { short: true, score: MIN_SCORE_ORANGE, codeIssues: 1, iconShown: true },
        { short: true, score: MIN_SCORE_GREEN, codeIssues: 2, iconShown: true },
        // show only if code issues exist
        { short: true, score: MIN_SCORE_GREEN, codeIssues: undefined, iconShown: false },
        { short: true, score: MIN_SCORE_GREEN, codeIssues: 0, iconShown: false },
        { short: true, score: MIN_SCORE_GREEN, codeIssues: 10, iconShown: true },
    ])('should show a warning icon if code issues exist (%s)', ({ short, score, codeIssues, iconShown }) => {
        const submission: Submission = { id: 1 };
        const result: Result = {
            id: 3,
            submission,
            score,
            testCaseCount: 2,
            codeIssueCount: codeIssues,
            completionDate: dayjs().subtract(2, 'minutes'),
        };
        const participation = cloneDeep(programmingParticipation);
        result.participation = participation;
        participation.results = [result];

        component.short = short;
        component.participation = participation;
        fixture.detectChanges();

        const warningIcon = fixture.debugElement.nativeElement.querySelector('#code-issue-warnings-icon');
        if (iconShown) {
            expect(warningIcon).toBeDefined();
        } else {
            expect(warningIcon).toBeNull();
        }
    });
});
