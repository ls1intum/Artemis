import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { FileUploadAssessmentService } from 'app/fileupload/manage/assess/file-upload-assessment.service';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';
import { ProgrammingAssessmentManualResultService } from 'app/programming/manage/assess/manual-result/programming-assessment-manual-result.service';
import { ManageAssessmentButtonsComponent } from 'app/exercise/exercise-scores/manage-assessment-buttons/manage-assessment-buttons.component';
import { TextAssessmentService } from 'app/text/manage/assess/service/text-assessment.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ManageAssessmentButtonsComponent', () => {
    let fixture: ComponentFixture<ManageAssessmentButtonsComponent>;
    let component: ManageAssessmentButtonsComponent;

    const programmingExercise = { type: ExerciseType.PROGRAMMING, assessmentType: AssessmentType.SEMI_AUTOMATIC } as Exercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ManageAssessmentButtonsComponent, MockComponent(FaIconComponent), MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(ProgrammingAssessmentManualResultService),
                MockProvider(TextAssessmentService),
                MockProvider(ModelingAssessmentService),
                MockProvider(FileUploadAssessmentService),
                MockProvider(ExamManagementService),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ManageAssessmentButtonsComponent);
        component = fixture.componentInstance;
    });

    beforeEach(() => {
        jest.restoreAllMocks();
    });

    it.each([
        [programmingExercise, [0]],
        [{ ...programmingExercise, exerciseGroup: { exam: { numberOfCorrectionRoundsInExam: 1 } } }, [0]],
        [{ ...programmingExercise, exerciseGroup: { exam: { numberOfCorrectionRoundsInExam: 2 } } }, [0, 1]],
    ])('should correctly initialize correctionRoundIndices', (exercise: Exercise, expectedIndices: number[]) => {
        component.exercise = exercise;
        component.participation = {} as Participation;

        fixture.changeDetectorRef.detectChanges();

        expect(component.correctionRoundIndices).toEqual(expectedIndices);
    });

    describe('newManualResultAllowed', () => {
        it('should not allow new manual results for quiz exercises', () => {
            component.exercise = { type: ExerciseType.QUIZ } as Exercise;
            component.participation = {} as Participation;

            fixture.changeDetectorRef.detectChanges();

            expect(component.newManualResultAllowed).toBeFalse();
        });

        it('should not allow new manual results for practice mode exercise', () => {
            component.exercise = programmingExercise;
            component.participation = { testRun: true } as Participation;

            fixture.changeDetectorRef.detectChanges();

            expect(component.newManualResultAllowed).toBeFalse();
        });

        it('should allow new manual results for exam test runs', () => {
            programmingExercise.exerciseGroup = { exam: {} } as ExerciseGroup;
            component.exercise = programmingExercise;
            component.participation = { testRun: true } as Participation;

            fixture.changeDetectorRef.detectChanges();

            expect(component.newManualResultAllowed).toBeTrue();
        });

        it('should allow new manual results for programming exercises with manual assessment', () => {
            component.exercise = programmingExercise;
            component.participation = { testRun: false } as Participation;

            fixture.changeDetectorRef.detectChanges();

            expect(component.newManualResultAllowed).toBeTrue();
        });
    });

    describe('getCorrectionRoundForAssessmentLink', () => {
        it('should increment the correction round if an accepted complaint is present', () => {
            component.exercise = programmingExercise;
            const submission = { id: 1, results: [{ id: 1, hasComplaint: true }, { id: 2 }] };

            component.participation = { submissions: [submission] };

            fixture.changeDetectorRef.detectChanges();
            const correctionRound = component.getCorrectionRoundForAssessmentLink(0);

            expect(correctionRound).toBe(1);
        });

        it('should not increment the correction round if the complaint did not get answered', () => {
            component.exercise = programmingExercise;
            const submission = { id: 1, results: [{ id: 1, hasComplaint: false }] };
            component.participation = { submissions: [submission] };

            fixture.changeDetectorRef.detectChanges();
            const correctionRound = component.getCorrectionRoundForAssessmentLink(0);

            expect(correctionRound).toBe(0);
        });
    });
});
