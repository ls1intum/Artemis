import { TestBed, ComponentFixture } from '@angular/core/testing';
import { TextSubmissionAssessmentComponent } from 'app/exercises/text/assess-new/text-submission-assessment.component';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisTestModule } from '../../test.module';
import { By } from '@angular/platform-browser';
import { AssessmentLayoutComponent } from 'app/assessment/assessment-layout/assessment-layout.component';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TextAssessmentAreaComponent } from 'app/exercises/text/assess-new/text-assessment-area/text-assessment-area.component';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TextblockAssessmentCardComponent } from 'app/exercises/text/assess-new/textblock-assessment-card/textblock-assessment-card.component';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess-new/textblock-feedback-editor/textblock-feedback-editor.component';
import { TranslateModule } from '@ngx-translate/core';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { SubmissionExerciseType, SubmissionType } from 'app/entities/submission.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Result } from 'app/entities/result.model';
import * as moment from 'moment';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ArtemisConfirmIconModule } from 'app/shared/confirm-icon/confirm-icon.module';
import { Course } from 'app/entities/course.model';
import { ManualTextblockSelectionComponent } from 'app/exercises/text/assess-new/manual-textblock-selection/manual-textblock-selection.component';
import { TextSharedModule } from 'app/exercises/text/shared/text-shared.module';

describe('TextSubmissionAssessmentComponent', () => {
    let component: TextSubmissionAssessmentComponent;
    let fixture: ComponentFixture<TextSubmissionAssessmentComponent>;

    const route = ({ snapshot: { path: '' } } as unknown) as ActivatedRoute;
    const exercise = {
        id: 20,
        type: ExerciseType.TEXT,
        assessmentType: AssessmentType.MANUAL,
        problemStatement: '',
        course: { id: 123, isAtLeastInstructor: true } as Course,
    } as TextExercise;
    const participation: StudentParticipation = ({
        type: ParticipationType.STUDENT,
        exercise,
    } as unknown) as StudentParticipation;
    const submission = ({
        submissionExerciseType: SubmissionExerciseType.TEXT,
        id: 2278,
        submitted: true,
        type: SubmissionType.MANUAL,
        submissionDate: moment('2019-07-09T10:47:33.244Z'),
        text: 'asdfasdfasdfasdf',
        participation,
    } as unknown) as TextSubmission;
    submission.result = ({
        id: 2374,
        resultString: '1 of 12 points',
        completionDate: moment('2019-07-09T11:51:23.251Z'),
        successful: false,
        score: 8,
        rated: true,
        hasFeedback: false,
        hasComplaint: false,
        submission,
        participation,
    } as unknown) as Result;
    submission.participation!.submissions = [submission];
    submission.participation!.results = [submission.result];

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                ArtemisSharedModule,
                ArtemisAssessmentSharedModule,
                AssessmentInstructionsModule,
                TranslateModule.forRoot(),
                ArtemisConfirmIconModule,
                RouterModule,
                TextSharedModule,
            ],
            declarations: [
                TextSubmissionAssessmentComponent,
                TextAssessmentAreaComponent,
                TextblockAssessmentCardComponent,
                TextblockFeedbackEditorComponent,
                ManualTextblockSelectionComponent,
            ],
            providers: [{ provide: ActivatedRoute, useValue: route }],
        })
            .overrideModule(ArtemisTestModule, {
                remove: {
                    declarations: [MockComponent(FaIconComponent)],
                    exports: [MockComponent(FaIconComponent)],
                },
            })
            .compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(TextSubmissionAssessmentComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should show jhi-text-assessment-area', () => {
        component['setPropertiesFromServerResponse'](participation);
        fixture.detectChanges();

        const textAssessmentArea = fixture.debugElement.query(By.directive(TextAssessmentAreaComponent));
        expect(textAssessmentArea).toBeTruthy();
    });

    it('should use jhi-assessment-layout', () => {
        const sharedLayout = fixture.debugElement.query(By.directive(AssessmentLayoutComponent));
        expect(sharedLayout).toBeTruthy();
    });

    it('should update score', () => {
        component['setPropertiesFromServerResponse'](participation);
        fixture.detectChanges();

        const textAssessmentArea = fixture.debugElement.query(By.directive(TextAssessmentAreaComponent));
        const textAssessmentAreaComponent = textAssessmentArea.componentInstance as TextAssessmentAreaComponent;
        const textBlockRef = TextBlockRef.new();
        textBlockRef.initFeedback();
        textBlockRef.feedback!.credits = 42;
        textAssessmentAreaComponent.textBlockRefs.push(textBlockRef);
        textAssessmentAreaComponent.textBlockRefsChangeEmit();

        expect(component.totalScore).toBe(42);
    });
});
