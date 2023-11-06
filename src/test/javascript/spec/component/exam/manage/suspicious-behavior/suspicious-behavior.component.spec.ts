import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';

import { SuspiciousBehaviorComponent } from 'app/exam/manage/suspicious-behavior/suspicious-behavior.component';
import { SuspiciousSessionsService } from 'app/exam/manage/suspicious-behavior/suspicious-sessions.service';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { PlagiarismResultsService } from 'app/course/plagiarism-cases/shared/plagiarism-results.service';
import { of, throwError } from 'rxjs';
import { ArtemisTestModule } from '../../../../test.module';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { PlagiarismCasesOverviewComponent } from 'app/exam/manage/suspicious-behavior/plagiarism-cases-overview/plagiarism-cases-overview.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockRouterLinkDirective } from '../../../../helpers/mocks/directive/mock-router-link.directive';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Exercise } from 'app/entities/exercise.model';
import { SuspiciousExamSessions, SuspiciousSessionReason } from 'app/entities/exam-session.model';
import { MockRouter } from '../../../../helpers/mocks/mock-router';
import { FormsModule } from '@angular/forms';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';

describe('SuspiciousBehaviorComponent', () => {
    let component: SuspiciousBehaviorComponent;
    let fixture: ComponentFixture<SuspiciousBehaviorComponent>;
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: 1, examId: 2 }) } } as unknown as ActivatedRoute;
    let suspiciousSessionService: SuspiciousSessionsService;
    let plagiarismCasesService: PlagiarismCasesService;
    let plagiarismResultsService: PlagiarismResultsService;
    let examService: ExamManagementService;
    let router: Router;
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
            {
                id: 1,
                ipAddress: '192.168.0.0',
                browserFingerprintHash: 'abc',
                suspiciousReasons: [SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS, SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT],
            },
            {
                id: 2,
                suspiciousReasons: [SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS, SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT],
                ipAddress: '192.168.0.0',
                browserFingerprintHash: 'abc',
            },
        ],
    } as SuspiciousExamSessions;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockRouterLinkDirective, MockModule(FormsModule)],
            declarations: [
                SuspiciousBehaviorComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(PlagiarismCasesOverviewComponent),
                MockComponent(ButtonComponent),
                MockComponent(DocumentationButtonComponent),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
            ],
        });
        fixture = TestBed.createComponent(SuspiciousBehaviorComponent);
        component = fixture.componentInstance;
        suspiciousSessionService = TestBed.inject(SuspiciousSessionsService);
        plagiarismCasesService = TestBed.inject(PlagiarismCasesService);
        plagiarismResultsService = TestBed.inject(PlagiarismResultsService);
        examService = TestBed.inject(ExamManagementService);
        router = TestBed.inject(Router);

        fixture.detectChanges();
    });

    it('should set course and exam id onInit', () => {
        component.ngOnInit();
        expect(component.courseId).toBe(1);
        expect(component.examId).toBe(2);
    });

    it('should not make a request onInit', () => {
        const suspiciousSessionsServiceSpy = jest.spyOn(suspiciousSessionService, 'getSuspiciousSessions').mockReturnValue(of([suspiciousSessions]));
        component.ngOnInit();
        expect(suspiciousSessionsServiceSpy).not.toHaveBeenCalledOnce();
    });

    it('should make a request on click', () => {
        const suspiciousSessionsServiceSpy = jest.spyOn(suspiciousSessionService, 'getSuspiciousSessions').mockReturnValue(of([suspiciousSessions]));
        component.checkboxCriterionDifferentStudentExamsSameIPAddressChecked = true;
        component.checkboxCriterionDifferentStudentExamsSameBrowserFingerprintChecked = true;
        component.analyzeSessions();
        expect(suspiciousSessionsServiceSpy).toHaveBeenCalledOnce();
        expect(suspiciousSessionsServiceSpy).toHaveBeenCalledWith(1, 2, {
            sameIpAddressDifferentStudentExams: true,
            sameBrowserFingerprintDifferentStudentExams: true,
            differentIpAddressesSameStudentExam: false,
            differentBrowserFingerprintsSameStudentExam: false,
            ipAddressOutsideOfRange: false,
        });
        expect(component.suspiciousSessions).toEqual([suspiciousSessions]);
    });

    it('should set analyzed to true and analyzing to false if the request fails', () => {
        jest.spyOn(suspiciousSessionService, 'getSuspiciousSessions').mockReturnValue(throwError({ status: 500 }));
        component.checkboxCriterionDifferentStudentExamsSameIPAddressChecked = true;
        component.checkboxCriterionDifferentStudentExamsSameBrowserFingerprintChecked = true;
        component.analyzeSessions();
        expect(component.analyzed).toBeTrue();
        expect(component.analyzing).toBeFalse();
    });

    it('should navigate to suspicious sessions on click', () => {
        const routerSpy = jest.spyOn(router, 'navigate');
        component.suspiciousSessions = [suspiciousSessions];
        fixture.detectChanges();
        fixture.debugElement.nativeElement.querySelector('#view-sessions-btn').click();
        expect(routerSpy).toHaveBeenCalledOnce();
        expect(routerSpy).toHaveBeenCalledWith(['/course-management', 1, 'exams', 2, 'suspicious-behavior', 'suspicious-sessions'], {
            state: { suspiciousSessions: [suspiciousSessions] },
        });
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
