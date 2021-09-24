import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService, TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { ModelingSubmissionViewerComponent } from 'app/exercises/shared/plagiarism/plagiarism-split-view/modeling-submission-viewer/modeling-submission-viewer.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { PlagiarismSubmission } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmission';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { SubmissionType } from 'app/entities/submission.model';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { SplitPaneHeaderComponent } from 'app/exercises/shared/plagiarism/plagiarism-split-view/split-pane-header/split-pane-header.component';

describe('Modeling Submission Viewer Component', () => {
    let comp: ModelingSubmissionViewerComponent;
    let fixture: ComponentFixture<ModelingSubmissionViewerComponent>;
    let modelingSubmissionService: ModelingSubmissionService;

    const modelingExercise = { id: 123, type: ExerciseType.MODELING } as ModelingExercise;
    const modelingSubmission = { id: 123, type: SubmissionType.MANUAL } as ModelingSubmission;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisModelingEditorModule, TranslateTestingModule],
            declarations: [ModelingSubmissionViewerComponent, SplitPaneHeaderComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
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
});
