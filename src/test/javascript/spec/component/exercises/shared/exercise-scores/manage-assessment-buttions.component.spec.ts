import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Participation } from 'app/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { FileUploadAssessmentService } from 'app/exercises/file-upload/assess/file-upload-assessment.service';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { ProgrammingAssessmentManualResultService } from 'app/exercises/programming/assess/manual-result/programming-assessment-manual-result.service';
import { ManageAssessmentButtonsComponent } from 'app/exercises/shared/exercise-scores/manage-assessment-buttons.component';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';

describe('ManageAssessmentButtonsComponent', () => {
    let fixture: ComponentFixture<ManageAssessmentButtonsComponent>;
    let component: ManageAssessmentButtonsComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
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

    describe('newManualResultAllowed', () => {
        it('should not allow new manual results for quiz exercises', () => {
            component.exercise = new QuizExercise();
            component.participation = {} as Participation;

            fixture.detectChanges();

            expect(component.newManualResultAllowed).toBeFalse();
        });

        it('should not allow new manual results for practice mode exercise', () => {
            const exercise = new ProgrammingExercise();
            exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
            component.exercise = exercise;
            component.participation = { testRun: true } as Participation;

            fixture.detectChanges();

            expect(component.newManualResultAllowed).toBeFalse();
        });

        it('should allow new manual results for exam test runs', () => {
            const exercise = new ProgrammingExercise();
            exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
            exercise.exerciseGroup = { exam: {} } as ExerciseGroup;
            component.exercise = exercise;
            component.participation = { testRun: true } as Participation;

            fixture.detectChanges();

            expect(component.newManualResultAllowed).toBeTrue();
        });

        it('should allow new manual results for programming exercises with manual assessment', () => {
            const exercise = new ProgrammingExercise();
            exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
            component.exercise = exercise;
            component.participation = { testRun: false } as Participation;

            fixture.detectChanges();

            expect(component.newManualResultAllowed).toBeTrue();
        });
    });

    describe('getCorrectionRoundForAssessmentLink', () => {
        it('should increment the correction round if an accepted complaint is present', () => {
            component.exercise = new ProgrammingExercise();
            component.participation = { results: [{ id: 1, hasComplaint: true }, { id: 2 }] };

            fixture.detectChanges();
            const correctionRound = component.getCorrectionRoundForAssessmentLink(0);

            expect(correctionRound).toBe(1);
        });

        it('should not increment the correction round if the complaint did not get answered', () => {
            component.exercise = new ProgrammingExercise();
            component.participation = { results: [{ id: 1, hasComplaint: true }] }; // no second result

            fixture.detectChanges();
            const correctionRound = component.getCorrectionRoundForAssessmentLink(0);

            expect(correctionRound).toBe(0);
        });
    });
});
