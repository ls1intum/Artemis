import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ExamChecklist } from 'app/entities/exam-checklist.model';
import { Exam } from 'app/entities/exam.model';
import { ExamChecklistExerciseGroupTableComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-exercisegroup-table/exam-checklist-exercisegroup-table.component';
import { ExamChecklistComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.component';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTestModule } from '../../../../test.module';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';
import { MockExamChecklistService } from '../../../../helpers/mocks/service/mock-exam-checklist.service';
import { of } from 'rxjs';

export function getExerciseGroups(equalPoints: boolean) {
    const dueDateStatArray = [{ inTime: 0, late: 0, total: 0 }];
    const exerciseGroups = [
        {
            id: 1,
            exercises: [
                { id: 3, maxPoints: 100, numberOfAssessmentsOfCorrectionRounds: dueDateStatArray, studentAssignedTeamIdComputed: false, secondCorrectionEnabled: false },
                { id: 2, maxPoints: 100, numberOfAssessmentsOfCorrectionRounds: dueDateStatArray, studentAssignedTeamIdComputed: false, secondCorrectionEnabled: false },
            ],
        },
    ];
    if (!equalPoints) {
        exerciseGroups[0].exercises[0].maxPoints = 50;
    }
    return exerciseGroups;
}

describe('ExamChecklistComponent', () => {
    let examChecklistComponentFixture: ComponentFixture<ExamChecklistComponent>;
    let component: ExamChecklistComponent;

    let examChecklistService: ExamChecklistService;

    const exam = new Exam();
    const examChecklist = new ExamChecklist();
    const dueDateStatArray = [{ inTime: 0, late: 0, total: 0 }];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                ExamChecklistComponent,
                MockPipe(ArtemisDatePipe),
                MockDirective(TranslateDirective),
                ExamChecklistExerciseGroupTableComponent,
                ProgressBarComponent,
                MockDirective(NgbTooltip),
            ],
            providers: [{ provide: ExamChecklistService, useClass: MockExamChecklistService }],
        })
            .compileComponents()
            .then(() => {
                examChecklistComponentFixture = TestBed.createComponent(ExamChecklistComponent);
                component = examChecklistComponentFixture.componentInstance;
                examChecklistService = TestBed.inject(ExamChecklistService);
            });
    });

    beforeEach(() => {
        // reset exam
        component.exam = exam;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });
    it('should count mandatory exercises correctly', () => {
        component.exam.exerciseGroups = getExerciseGroups(true);

        component.ngOnChanges();

        expect(component.countMandatoryExercises).toBe(0);
        expect(component.hasOptionalExercises).toBeTrue();

        component.exam.exerciseGroups[0].isMandatory = true;

        component.ngOnChanges();

        expect(component.countMandatoryExercises).toBe(1);
        expect(component.hasOptionalExercises).toBeFalse();

        const additionalExerciseGroup = {
            id: 13,
            exercises: [{ id: 23, maxPoints: 100, numberOfAssessmentsOfCorrectionRounds: dueDateStatArray, studentAssignedTeamIdComputed: false, secondCorrectionEnabled: false }],
        };
        component.exam.exerciseGroups.push(additionalExerciseGroup);

        component.ngOnChanges();

        expect(component.countMandatoryExercises).toBe(1);
        expect(component.hasOptionalExercises).toBeTrue();
    });

    it('should set exam checklist correctly', () => {
        const getExamStatisticsStub = jest.spyOn(examChecklistService, 'getExamStatistics').mockReturnValue(of(examChecklist));

        component.ngOnChanges();

        expect(getExamStatisticsStub).toHaveBeenCalledOnce();
        expect(getExamStatisticsStub).toHaveBeenCalledWith(exam);
        expect(component.examChecklist).toEqual(examChecklist);
    });
});
