import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ModelingSubmissionElement } from 'app/plagiarism/shared/types/modeling/ModelingSubmissionElement';
import { ModelingSubmissionViewerComponent } from 'app/plagiarism/manage/plagiarism-split-view/modeling-submission-viewer/modeling-submission-viewer.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { PlagiarismSubmission } from 'app/plagiarism/shared/types/PlagiarismSubmission';
import { ModelingSubmissionService } from 'app/modeling/overview/modeling-submission.service';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { SubmissionType } from 'app/entities/submission.model';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('Modeling Submission Viewer Component', () => {
    let comp: ModelingSubmissionViewerComponent;
    let fixture: ComponentFixture<ModelingSubmissionViewerComponent>;
    let modelingSubmissionService: ModelingSubmissionService;

    const modelingExercise = { id: 123, type: ExerciseType.MODELING } as ModelingExercise;
    const modelingSubmission = { id: 123, type: SubmissionType.MANUAL } as ModelingSubmission;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ModelingSubmissionViewerComponent);
        comp = fixture.componentInstance;
        modelingSubmissionService = fixture.debugElement.injector.get(ModelingSubmissionService);

        comp.plagiarismSubmission = { studentLogin: 'studentA' } as PlagiarismSubmission<ModelingSubmissionElement>;
        comp.exercise = modelingExercise;
    });

    it('should fetch the submission', () => {
        jest.spyOn(modelingSubmissionService, 'getSubmissionWithoutLock').mockReturnValue(of(modelingSubmission));

        comp.ngOnChanges({
            plagiarismSubmission: {
                previousValue: null,
                isFirstChange: () => true,
                firstChange: true,
                currentValue: { submissionId: 1 },
            },
        });

        expect(modelingSubmissionService.getSubmissionWithoutLock).toHaveBeenCalledWith(1);
    });

    it('should not fetch the submission if hideContent is true', () => {
        jest.spyOn(modelingSubmissionService, 'getSubmissionWithoutLock').mockReturnValue(of(modelingSubmission));
        comp.hideContent = true;

        comp.ngOnChanges({
            plagiarismSubmission: {
                previousValue: null,
                isFirstChange: () => true,
                firstChange: true,
                currentValue: { submissionId: 1 },
            },
        });

        expect(modelingSubmissionService.getSubmissionWithoutLock).not.toHaveBeenCalled();
    });
});
