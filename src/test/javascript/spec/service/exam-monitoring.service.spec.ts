import { TestBed } from '@angular/core/testing';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ArtemisTestModule } from '../test.module';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { StudentExam } from 'app/entities/student-exam.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import {
    ConnectionUpdatedAction,
    ContinuedAfterHandedInEarlyAction,
    EndedExamAction,
    ExamAction,
    ExamActionType,
    ExamActivity,
    HandedInEarlyAction,
    SavedExerciseAction,
    StartedExamAction,
    SwitchedExerciseAction,
} from 'app/entities/exam-user-activity.model';
import { ExamMonitoringService } from 'app/exam/monitor/exam-monitoring.service';

describe('ExamMonitoringService', () => {
    let service: ExamMonitoringService;
    let exam: Exam;
    let studentExam: StudentExam;
    let course: Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [ArtemisServerDateService, ExamMonitoringService],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(ExamMonitoringService);
            });
    });

    beforeEach(() => {
        // reset exam
        exam = new Exam();
        exam.id = 1;
        exam.monitoring = true;

        // reset studentExam
        studentExam = new StudentExam();
        studentExam.id = 1;

        // reset course
        course = new Course();
        course.id = 1;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should handle exam actions when monitoring enabled', () => {
        const examActions = createExamActions();
        expect(examActions.length).toBe(Object.keys(ExamActionType).length);
        examActions.forEach((action) => {
            service.handleActionEvent(studentExam, action, exam.monitoring!);
            expect(studentExam.examActivity).not.toBeUndefined();
            expect(studentExam.examActivity!.examActions).not.toBeUndefined();
            expect(studentExam.examActivity!.examActions.length).toBe(1);
            expect(studentExam.examActivity!.examActions.first()).toBe(action);
            studentExam.examActivity!.examActions = [];
        });
    });

    it('should not handle exam actions when monitoring disabled', () => {
        exam.monitoring = false;
        const examActions = createExamActions();
        expect(examActions.length).toBe(Object.keys(ExamActionType).length);
        examActions.forEach((action) => {
            service.handleActionEvent(studentExam, action, exam.monitoring!);
            expect(studentExam.examActivity).toBeUndefined();
        });
    });

    it('should sync actions when monitoring enabled', () => {
        const spy = jest.spyOn(service, 'syncActions').mockReturnValue(httpResponse());
        studentExam.examActivity = new ExamActivity();
        service.saveActions(exam, studentExam, course.id!);
        expect(spy).toHaveBeenCalled();
        expect(spy).toHaveBeenCalledWith([], course.id, exam.id, studentExam.id);
    });

    it('should not sync actions when monitoring disabled', () => {
        const spy = jest.spyOn(service, 'syncActions').mockReturnValue(httpResponse());
        studentExam.examActivity = new ExamActivity();
        exam.monitoring = false;
        service.saveActions(exam, studentExam, course.id!);
        expect(spy).not.toHaveBeenCalled();
    });

    it('should remove actions after successful sync', () => {
        const spy = jest.spyOn(service, 'syncActions').mockReturnValue(httpResponse());

        studentExam.examActivity = new ExamActivity();
        const examActions = createExamActions();
        studentExam.examActivity.examActions = examActions;
        service.saveActions(exam, studentExam, course.id!);
        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(examActions, course.id, exam.id, studentExam.id);

        expect(studentExam.examActivity.examActions.length).toBe(0);
    });

    it('should not remove actions after not successful sync', () => {
        const spy = jest.spyOn(service, 'syncActions');

        const examActions = createExamActions();
        studentExam.examActivity = new ExamActivity();
        studentExam.examActivity.examActions = examActions;
        service.saveActions(exam, studentExam, course.id!);

        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(examActions, course.id, exam.id, studentExam.id);

        expect(studentExam.examActivity.examActions.length).toBe(examActions.length);
    });

    it('should return the correct resource url', () => {
        const spy = jest.spyOn(service, 'getResourceURL');
        const url = service.getResourceURL(course.id!, exam.id!);

        expect(spy).toHaveBeenCalledOnce();

        expect(url).toEqual(`${SERVER_API_URL}api/courses/${course.id!}/exams/${exam.id!}`);
    });
});

const createExamActions = (): ExamAction[] => {
    return Object.keys(ExamActionType).map((type) => createExamActionBasedOnType(ExamActionType[type]));
};

const createExamActionBasedOnType = (examActionType: ExamActionType): ExamAction => {
    let examAction: ExamAction;

    switch (examActionType) {
        case ExamActionType.STARTED_EXAM:
            examAction = new StartedExamAction(0);
            break;
        case ExamActionType.ENDED_EXAM:
            examAction = new EndedExamAction();
            break;
        case ExamActionType.HANDED_IN_EARLY:
            examAction = new HandedInEarlyAction();
            break;
        case ExamActionType.CONTINUED_AFTER_HAND_IN_EARLY:
            examAction = new ContinuedAfterHandedInEarlyAction();
            break;
        case ExamActionType.SWITCHED_EXERCISE:
            examAction = new SwitchedExerciseAction(0);
            break;
        case ExamActionType.SAVED_EXERCISE:
            examAction = new SavedExerciseAction(false, 0, false, true);
            break;
        case ExamActionType.CONNECTION_UPDATED:
            examAction = new ConnectionUpdatedAction(false);
            break;
    }
    return examAction;
};

const httpResponse = (body?: any) => of(new HttpResponse({ body }));
