import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { MockComponent, MockPipe } from 'ng-mocks';
import { Observable, of } from 'rxjs';

import { AssessmentType } from 'app/entities/assessment-type.model';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { ProgrammingExercise, ProjectType } from 'app/entities/programming-exercise.model';
import { SubmissionPolicyType } from 'app/entities/submission-policy.model';
import { ProgrammingExerciseProblemComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-problem.component';
import { ProgrammingExerciseUpdateWizardProblemComponent } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard-problem.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('ProgrammingExerciseWizardProblemComponent', () => {
    let wizardComponentFixture: ComponentFixture<ProgrammingExerciseUpdateWizardProblemComponent>;
    let wizardComponent: ProgrammingExerciseUpdateWizardProblemComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [ProgrammingExerciseUpdateWizardProblemComponent, MockComponent(ProgrammingExerciseProblemComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: { queryParams: of({}) },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                wizardComponentFixture = TestBed.createComponent(ProgrammingExerciseUpdateWizardProblemComponent);
                wizardComponent = wizardComponentFixture.componentInstance;

                wizardComponent.problemStepInputs = {
                    checkoutSolutionRepositoryAllowed: false,
                    hasUnsavedChanges: false,
                    inProductionEnvironment: false,
                    onRecreateBuildPlanOrUpdateTemplateChange(): void {},
                    problemStatementLoaded: false,
                    recreateBuildPlans: false,
                    rerenderSubject: new Observable<void>(),
                    selectedProjectType: ProjectType.PLAIN,
                    sequentialTestRunsAllowed: false,
                    templateParticipationResultLoaded: false,
                    updateTemplate: false,
                    validIdeSelection(): boolean | undefined {
                        return undefined;
                    },
                };

                const exercise = new ProgrammingExercise(undefined, undefined);
                exercise.maxPoints = 10;
                exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
                exercise.assessmentType = AssessmentType.AUTOMATIC;
                exercise.submissionPolicy = { type: SubmissionPolicyType.NONE };

                wizardComponent.exercise = exercise;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', fakeAsync(() => {
        wizardComponentFixture.detectChanges();
        expect(wizardComponent).not.toBeNull();
    }));
});
