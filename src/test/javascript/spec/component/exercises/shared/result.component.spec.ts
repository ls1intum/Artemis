import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { Result } from 'app/entities/result.model';
import { ArtemisTestModule } from '../../../test.module';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { cloneDeep } from 'lodash-es';
import { Submission } from 'app/entities/submission.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { faTimesCircle } from '@fortawesome/free-regular-svg-icons';

describe('ResultComponent', () => {
    let fixture: ComponentFixture<ResultComponent>;
    let component: ResultComponent;

    const result = { id: 0, participation: {}, submission: {} } as Result;
    const modelingExercise = { id: 1, type: ExerciseType.MODELING } as ModelingExercise;
    const participation = { id: 2, type: ParticipationType.STUDENT, exercise: modelingExercise } as Participation;

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

    it('should set results', () => {
        const submission1 = { id: 1 } as Submission;
        const result1 = { id: 1, submission: submission1, score: 1 } as Result;
        const result2 = { id: 2 } as Result;
        const participation1 = cloneDeep(participation);
        participation1.results = [result1, result2];
        component.participation = participation1;

        fixture.detectChanges();

        expect(component.result).toEqual(result1);
        expect(component.result!.participation).toEqual(participation1);
        expect(component.submission).toEqual(submission1);
        expect(component.textColorClass).toEqual('text-danger');
        expect(component.hasFeedback).toBeFalse();
        expect(component.resultIconClass).toEqual(faTimesCircle);
        expect(component.resultString).toEqual('artemisApp.editor.buildSuccessful');
    });
});
