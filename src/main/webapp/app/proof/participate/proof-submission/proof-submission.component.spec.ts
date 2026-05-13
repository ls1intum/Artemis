import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { ProofSubmissionComponent } from 'app/proof/participate/proof-submission/proof-submission.component';
import { ProofSubmissionService } from 'app/proof/participate/service/proof-submission.service';
import { ProofExercise } from 'app/proof/shared/entities/proof-exercise.model';
import { ProofSubmission } from 'app/proof/shared/entities/proof-submission.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { HeaderParticipationPageComponent } from 'app/exercise/exercise-headers/participation-page/header-participation-page.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ExerciseSubmitButtonComponent } from 'app/exercise/shared/exercise-submit-button/exercise-submit-button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

describe('ProofSubmissionComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ProofSubmissionComponent;
    let fixture: ComponentFixture<ProofSubmissionComponent>;
    let proofSubmissionService: ProofSubmissionService;
    let alertService: AlertService;

    const mockExercise = (): ProofExercise => {
        const ex = new ProofExercise(undefined, undefined);
        ex.id = 10;
        ex.type = ExerciseType.PROOF;
        return ex;
    };

    const mockParticipation = (exercise: ProofExercise): StudentParticipation => {
        return { id: 42, exercise } as StudentParticipation;
    };

    const mockSubmission = (participation: StudentParticipation): ProofSubmission => {
        const sub = new ProofSubmission();
        sub.id = 5;
        sub.submitted = false;
        sub.participation = participation;
        return sub;
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ProofSubmissionComponent],
            providers: [
                MockProvider(AlertService),
                MockProvider(ProofSubmissionService),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: { paramMap: convertToParamMap({ participationId: '42' }) },
                    },
                },
            ],
            declarations: [],
        }).overrideComponent(ProofSubmissionComponent, {
            remove: { imports: [HeaderParticipationPageComponent, ButtonComponent, ExerciseSubmitButtonComponent] },
            add: {
                imports: [
                    MockComponent(HeaderParticipationPageComponent),
                    MockComponent(ButtonComponent),
                    MockComponent(ExerciseSubmitButtonComponent),
                    MockDirective(TranslateDirective),
                    MockPipe(ArtemisTranslatePipe),
                    MockPipe(HtmlForMarkdownPipe),
                ],
            },
        });

        fixture = TestBed.createComponent(ProofSubmissionComponent);
        component = fixture.componentInstance;
        proofSubmissionService = TestBed.inject(ProofSubmissionService);
        alertService = TestBed.inject(AlertService);
    });

    it('should load participation and existing submission on init', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(proofSubmissionService, 'getDataForProofEditor').mockReturnValue(of(new HttpResponse({ body: submission })));

        fixture.detectChanges();

        expect(component.proofExercise).toBe(exercise);
        expect(component.participation).toBe(participation);
        expect(component.submission).toBe(submission);
    });

    it('should show error alert when loading fails', () => {
        vi.spyOn(proofSubmissionService, 'getDataForProofEditor').mockReturnValue(throwError(() => new Error('network')));
        const errorSpy = vi.spyOn(alertService, 'error');

        fixture.detectChanges();

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.proofExercise.error');
    });

    it('should call create when saving a new submission', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = new ProofSubmission();
        submission.participation = participation;
        vi.spyOn(proofSubmissionService, 'getDataForProofEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        const createSpy = vi.spyOn(proofSubmissionService, 'create').mockReturnValue(of(new HttpResponse({ body: submission })));
        fixture.detectChanges();

        component.save();

        expect(createSpy).toHaveBeenCalled();
    });

    it('should call update when saving an existing submission', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(proofSubmissionService, 'getDataForProofEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        const updateSpy = vi.spyOn(proofSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
        fixture.detectChanges();

        component.save();

        expect(updateSpy).toHaveBeenCalled();
    });

    it('should set submitted=true and call update on submit', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(proofSubmissionService, 'getDataForProofEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        const submittedSub = { ...submission, submitted: true, results: [{ score: 100 }] };
        const updateSpy = vi.spyOn(proofSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: submittedSub as any })));
        fixture.detectChanges();

        component.submit();

        expect(updateSpy).toHaveBeenCalled();
        expect(component.submission.submitted).toBe(true);
        expect(component.result?.score).toBe(100);
    });

    it('should revert submitted=false when submit fails', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(proofSubmissionService, 'getDataForProofEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        vi.spyOn(proofSubmissionService, 'update').mockReturnValue(throwError(() => new Error('server error')));
        fixture.detectChanges();

        component.submit();

        expect(component.submission.submitted).toBe(false);
    });
});
