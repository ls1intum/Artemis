import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';

import { SuspiciousBehaviorComponent } from 'app/exam/manage/suspicious-behavior/suspicious-behavior.component';
import { SuspiciousSessionsService } from 'app/exam/manage/suspicious-behavior/suspicious-sessions.service';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { PlagiarismResultsService } from 'app/course/plagiarism-cases/shared/plagiarism-results.service';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../../../test.module';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { PlagiarismCasesOverviewComponent } from 'app/exam/manage/suspicious-behavior/plagiarism-cases-overview/plagiarism-cases-overview.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockRouterLinkDirective } from '../../../../helpers/mocks/directive/mock-router-link.directive';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Exercise } from 'app/entities/exercise.model';
import { SuspiciousExamSessions, SuspiciousSessionReason } from 'app/entities/exam-session.model';

describe('SuspiciousBehaviorComponent', () => {
    let component: SuspiciousBehaviorComponent;
    let fixture: ComponentFixture<SuspiciousBehaviorComponent>;
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: 1, examId: 2 }) } } as unknown as ActivatedRoute;
    let suspiciousSessionService: SuspiciousSessionsService;
    let plagiarismCasesService: PlagiarismCasesService;
    let plagiarismResultsService: PlagiarismResultsService;
    let examService: ExamManagementService;
    const exercise1 = {
        id: 1,
        exerciseGroup: {
            id: 1,
            exam: {
                id: 1,
                course: {
                    id: 1,
                },
            },
        },
    } as Exercise;
    const exercise2 = {
        id: 2,
        exerciseGroup: {
            id: 2,
            exam: {
                id: 2,
                course: {
                    id: 2,
                },
            },
        },
    } as Exercise;

    const suspiciousSessions = {
        examSessions: [
            { id: 1, userAgent: 'user-agent', ipAddress: '192.168.0.0', suspiciousReasons: [SuspiciousSessionReason.SAME_IP_ADDRESS, SuspiciousSessionReason.SAME_USER_AGENT] },
            { id: 2, suspiciousReasons: [SuspiciousSessionReason.SAME_USER_AGENT, SuspiciousSessionReason.SAME_IP_ADDRESS], userAgent: 'user-agent', ipAddress: '192.168.0.0' },
        ],
    } as SuspiciousExamSessions;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockRouterLinkDirective],
            declarations: [SuspiciousBehaviorComponent, MockPipe(ArtemisTranslatePipe), MockComponent(PlagiarismCasesOverviewComponent), MockComponent(ButtonComponent)],
            providers: [{ provide: ActivatedRoute, useValue: route }],
        });
        fixture = TestBed.createComponent(SuspiciousBehaviorComponent);
        component = fixture.componentInstance;
        suspiciousSessionService = TestBed.inject(SuspiciousSessionsService);
        plagiarismCasesService = TestBed.inject(PlagiarismCasesService);
        plagiarismResultsService = TestBed.inject(PlagiarismResultsService);
        examService = TestBed.inject(ExamManagementService);

        fixture.detectChanges();
    });

    it('should set course and exam id onInit', () => {
        component.ngOnInit();
        expect(component.courseId).toBe(1);
        expect(component.examId).toBe(2);
    });

    it('should retrieve suspicious sessions onInit', () => {
        const suspiciousSessionsServiceSpy = jest.spyOn(suspiciousSessionService, 'getSuspiciousSessions').mockReturnValue(of([suspiciousSessions]));
        component.ngOnInit();
        expect(suspiciousSessionsServiceSpy).toHaveBeenCalledOnce();
        expect(suspiciousSessionsServiceSpy).toHaveBeenCalledWith(1, 2);
        expect(component.suspiciousSessions).toEqual([suspiciousSessions]);
    });
    it('should retrieve plagiarism cases/results onInit', () => {
        const examServiceSpy = jest.spyOn(examService, 'getExercisesWithPotentialPlagiarismForExam').mockReturnValue(of([exercise1, exercise2]));
        const plagiarismCasesServiceSpy = jest.spyOn(plagiarismCasesService, 'getNumberOfPlagiarismCasesForExercise').mockReturnValueOnce(of(0)).mockReturnValueOnce(of(1));
        const plagiarismResultsServiceSpy = jest.spyOn(plagiarismResultsService, 'getNumberOfPlagiarismResultsForExercise').mockReturnValueOnce(of(2)).mockReturnValueOnce(of(4));
        component.ngOnInit();
        expect(examServiceSpy).toHaveBeenCalledOnce();
        expect(examServiceSpy).toHaveBeenCalledWith(1, 2);
        expect(component.exercises).toEqual([exercise1, exercise2]);
        expect(plagiarismCasesServiceSpy).toHaveBeenCalledTimes(2);
        expect(plagiarismCasesServiceSpy).toHaveBeenCalledWith(exercise1);
        expect(plagiarismCasesServiceSpy).toHaveBeenCalledWith(exercise2);
        expect(component.plagiarismCasesPerExercise).toEqual(
            new Map([
                [exercise1, 0],
                [exercise2, 1],
            ]),
        );
        expect(component.anyPlagiarismCases).toBeTrue();
        expect(plagiarismResultsServiceSpy).toHaveBeenCalledTimes(2);
        expect(plagiarismResultsServiceSpy).toHaveBeenCalledWith(1);
        expect(plagiarismResultsServiceSpy).toHaveBeenCalledWith(2);
        expect(component.plagiarismResultsPerExercise).toEqual(
            new Map([
                [exercise1, 2],
                [exercise2, 4],
            ]),
        );
    });
});
