import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { NgModel } from '@angular/forms';
import { MockComponent, MockDirective } from 'ng-mocks';
import { PlagiarismCasesListComponent } from 'app/course/plagiarism-cases/plagiarism-cases-list.component';
import { ArtemisTestModule } from '../../test.module';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/plagiarism-cases.service';
import { PlagiarismCase } from 'app/course/plagiarism-cases/types/PlagiarismCase';
import { ExerciseType } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { PlagiarismSplitViewComponent } from 'app/exercises/shared/plagiarism/plagiarism-split-view/plagiarism-split-view.component';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { Notification } from 'app/entities/notification.model';

describe('Plagiarism Cases List', () => {
    let comp: PlagiarismCasesListComponent;
    let fixture: ComponentFixture<PlagiarismCasesListComponent>;
    let plagiarismCasesService: PlagiarismCasesService;
    let sendPlagiarismNotificationStub = jest.SpyInstance;
    let updatePlagiarismStatusStub = jest.SpyInstance;

    const studentLoginA = 'student1A';
    const studentLoginB = 'student1B';

    const notificationA = {
        text: 'notification for student A',
    } as Notification;
    const notificationB = {
        text: 'notification for student B',
    } as Notification;

    const plagiarismComparison = {
        id: 1,
        submissionA: { studentLogin: studentLoginA },
        submissionB: { studentLogin: studentLoginB },
        similarity: 0.5,
        status: PlagiarismStatus.NONE,
    } as PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;

    const textExercise = { id: 234, type: ExerciseType.TEXT } as TextExercise;

    const plagiarismCase = {
        exercise: textExercise,
        comparisons: [plagiarismComparison],
    } as PlagiarismCase;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [PlagiarismCasesListComponent, TranslatePipeMock, MockDirective(NgModel), MockComponent(PlagiarismSplitViewComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PlagiarismCasesListComponent);
                comp = fixture.componentInstance;

                plagiarismCasesService = TestBed.inject(PlagiarismCasesService);
                sendPlagiarismNotificationStub = jest.spyOn(plagiarismCasesService, 'sendPlagiarismNotification');
                updatePlagiarismStatusStub = jest.spyOn(plagiarismCasesService, 'updatePlagiarismStatus').mockReturnValue(of({}));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should student A be notified', () => {
        comp.plagiarismCase = plagiarismCase;
        comp.plagiarismCase.comparisons[0].notificationA = notificationA;

        expect(comp.isStudentANotified(0)).toBe(true);
    });

    it('should student A be not notified', () => {
        comp.plagiarismCase = plagiarismCase;
        comp.plagiarismCase.comparisons[0].notificationA = undefined;
        comp.plagiarismCase.comparisons[0].notificationB = undefined;

        expect(comp.isStudentANotified(0)).toBe(false);
    });

    it('should student B be notified', () => {
        comp.plagiarismCase = plagiarismCase;
        comp.plagiarismCase.comparisons[0].notificationB = notificationB;

        expect(comp.isStudentBNotified(0)).toBe(true);
    });

    it('should student B be not notified', () => {
        comp.plagiarismCase = plagiarismCase;
        comp.plagiarismCase.comparisons[0].notificationA = undefined;
        comp.plagiarismCase.comparisons[0].notificationB = undefined;

        expect(comp.isStudentBNotified(0)).toBe(false);
    });

    it('should hide notification form', () => {
        comp.activeStudentLogin = studentLoginA;
        comp.activeComparisonId = plagiarismComparison.id;

        comp.hideNotificationForm();

        expect(comp.activeStudentLogin).toBe(undefined);
        expect(comp.activeComparisonId).toBe(undefined);
    });

    it('should show notification form', () => {
        comp.showNotificationForm(studentLoginA, plagiarismComparison.id);

        expect(comp.activeStudentLogin).toBe(studentLoginA);
        expect(comp.activeComparisonId).toBe(plagiarismComparison.id);
    });

    it('should show comparison', () => {
        comp.showComparison(1);

        expect(comp.activeSplitViewComparison).toBe(1);
    });

    it('should send notification to student a', () => {
        comp.plagiarismCase = plagiarismCase;
        comp.notificationText = 'notification text a';
        sendPlagiarismNotificationStub.mockReturnValue(of(notificationA));

        comp.sendNotification('A', 0);

        expect(comp.plagiarismCase.comparisons[0].notificationA).toEqual(notificationA);
        expect(sendPlagiarismNotificationStub).toHaveBeenCalledWith(studentLoginA, plagiarismComparison.id, 'notification text a');
    });

    it('should send notification to student b', () => {
        comp.plagiarismCase = plagiarismCase;
        comp.notificationText = 'notification text b';
        sendPlagiarismNotificationStub.mockReturnValue(of(notificationB));

        comp.sendNotification('B', 0);

        expect(comp.plagiarismCase.comparisons[0].notificationB).toEqual(notificationB);
        expect(sendPlagiarismNotificationStub).toHaveBeenCalledWith(studentLoginB, plagiarismComparison.id, 'notification text b');
    });

    it('should update status to confirmed for student a', () => {
        comp.plagiarismCase = plagiarismCase;

        comp.updateStatus(true, 0, studentLoginA);

        expect(comp.plagiarismCase.comparisons[0].statusA).toEqual(PlagiarismStatus.CONFIRMED);
        expect(updatePlagiarismStatusStub).toHaveBeenCalledWith(true, plagiarismComparison.id, studentLoginA);
    });

    it('should update status to denied for student a', () => {
        comp.plagiarismCase = plagiarismCase;

        comp.updateStatus(false, 0, studentLoginA);

        expect(comp.plagiarismCase.comparisons[0].statusA).toEqual(PlagiarismStatus.DENIED);
        expect(updatePlagiarismStatusStub).toHaveBeenCalledWith(false, plagiarismComparison.id, studentLoginA);
    });

    it('should update status to confirmed for student b', () => {
        comp.plagiarismCase = plagiarismCase;

        comp.updateStatus(true, 0, studentLoginB);

        expect(comp.plagiarismCase.comparisons[0].statusB).toEqual(PlagiarismStatus.CONFIRMED);
        expect(updatePlagiarismStatusStub).toHaveBeenCalledWith(true, plagiarismComparison.id, studentLoginB);
    });

    it('should update status to denied for student b', () => {
        comp.plagiarismCase = plagiarismCase;

        comp.updateStatus(false, 0, studentLoginB);

        expect(comp.plagiarismCase.comparisons[0].statusB).toEqual(PlagiarismStatus.DENIED);
        expect(updatePlagiarismStatusStub).toHaveBeenCalledWith(false, plagiarismComparison.id, studentLoginB);
    });
});
