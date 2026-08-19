import { ExamUser } from 'app/exam/shared/entities/exam-user.model';
import dayjs from 'dayjs/esm';
import { Course } from 'app/course/shared/entities/course.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { BaseEntity } from 'app/foundation/model/base-entity';
import { ExamMode } from 'app/exam/shared/entities/exam-mode.model';

export class Exam implements BaseEntity {
    public id?: number;
    public title?: string;
    public examMode?: ExamMode;
    public examWithAttendanceCheck?: boolean;
    public visibleDate?: dayjs.Dayjs;
    public startDate?: dayjs.Dayjs;
    public endDate?: dayjs.Dayjs;
    // Default exam working time in seconds
    public workingTime?: number;
    public publishResultsDate?: dayjs.Dayjs;
    public examStudentReviewStart?: dayjs.Dayjs;
    public examStudentReviewEnd?: dayjs.Dayjs;
    public exampleSolutionPublicationDate?: dayjs.Dayjs;
    // date from which students can see the summary (submission overview) of their submitted exam; if unset the summary is shown immediately after submission
    public examSummaryPublicationDate?: dayjs.Dayjs;
    // grace period in seconds - time in which students can still submit even though working time is over
    public gracePeriod?: number;
    public examiner?: string;
    public moduleNumber?: string;
    public courseName?: string;

    public startText?: string;
    public endText?: string;
    public confirmationStartText?: string;
    public confirmationEndText?: string;
    public examMaxPoints?: number;
    public randomizeExerciseOrder?: boolean;
    public numberOfExercisesInExam?: number;
    public numberOfCorrectionRoundsInExam?: number;
    public course?: Course;
    public exerciseGroups?: ExerciseGroup[];
    public studentExams?: StudentExam[];
    public examUsers?: ExamUser[];
    public quizExamMaxPoints?: number;
    public numberOfExamUsers?: number; // transient
    public channelName?: string; // transient

    // helper attributes
    public visible?: boolean;
    public started?: boolean;

    public examArchivePath?: string;

    public latestIndividualEndDate?: dayjs.Dayjs;

    constructor() {
        this.randomizeExerciseOrder = false; // default value (set by server)
        this.numberOfCorrectionRoundsInExam = 1; // default value
        this.examMaxPoints = 1; // default value
        this.workingTime = 0; // will be updated during creation
        this.examMode = ExamMode.REAL; // default value
        this.examWithAttendanceCheck = false; // default value

        // helper attributes (calculated by the server at the time of the last request)
        this.visible = false;
        this.started = false;
    }

    /**
     * Returns a new Exam holding the same values, with every nested value carried over as it is.
     *
     * This is deliberately not a {@link deepClone}: it exists so a consumer bound to an `exam` signal is notified
     * after the object was updated in place (e.g. a live schedule change), while the exercise groups and the course
     * stay the same instances. A signal's `equal` option cannot do this, because Angular compares a property binding
     * with `Object.is` before it reaches a child's `input()`, so the same reference never arrives at the child.
     *
     * Keep this in sync with the fields above.
     */
    static withSameValues(exam: Exam): Exam {
        const rebuilt = new Exam();
        rebuilt.id = exam.id;
        rebuilt.title = exam.title;
        rebuilt.examMode = exam.examMode;
        rebuilt.examWithAttendanceCheck = exam.examWithAttendanceCheck;
        rebuilt.visibleDate = exam.visibleDate;
        rebuilt.startDate = exam.startDate;
        rebuilt.endDate = exam.endDate;
        rebuilt.workingTime = exam.workingTime;
        rebuilt.publishResultsDate = exam.publishResultsDate;
        rebuilt.examStudentReviewStart = exam.examStudentReviewStart;
        rebuilt.examStudentReviewEnd = exam.examStudentReviewEnd;
        rebuilt.exampleSolutionPublicationDate = exam.exampleSolutionPublicationDate;
        rebuilt.examSummaryPublicationDate = exam.examSummaryPublicationDate;
        rebuilt.gracePeriod = exam.gracePeriod;
        rebuilt.examiner = exam.examiner;
        rebuilt.moduleNumber = exam.moduleNumber;
        rebuilt.courseName = exam.courseName;
        rebuilt.startText = exam.startText;
        rebuilt.endText = exam.endText;
        rebuilt.confirmationStartText = exam.confirmationStartText;
        rebuilt.confirmationEndText = exam.confirmationEndText;
        rebuilt.examMaxPoints = exam.examMaxPoints;
        rebuilt.randomizeExerciseOrder = exam.randomizeExerciseOrder;
        rebuilt.numberOfExercisesInExam = exam.numberOfExercisesInExam;
        rebuilt.numberOfCorrectionRoundsInExam = exam.numberOfCorrectionRoundsInExam;
        rebuilt.course = exam.course;
        rebuilt.exerciseGroups = exam.exerciseGroups;
        rebuilt.studentExams = exam.studentExams;
        rebuilt.examUsers = exam.examUsers;
        rebuilt.quizExamMaxPoints = exam.quizExamMaxPoints;
        rebuilt.numberOfExamUsers = exam.numberOfExamUsers;
        rebuilt.channelName = exam.channelName;
        rebuilt.visible = exam.visible;
        rebuilt.started = exam.started;
        rebuilt.examArchivePath = exam.examArchivePath;
        rebuilt.latestIndividualEndDate = exam.latestIndividualEndDate;
        return rebuilt;
    }
}
