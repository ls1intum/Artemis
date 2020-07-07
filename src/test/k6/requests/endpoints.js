export const PROGRAMMING_EXERCISES_SETUP = '/programming-exercises/setup';
export const PROGRAMMING_EXERCISES = '/programming-exercises';
export const PROGRAMMING_EXERCISE = (exerciseId) => `${PROGRAMMING_EXERCISES}/${exerciseId}`;
export const QUIZ_EXERCISES = '/quiz-exercises';
export const QUIZ_EXERCISE = (exerciseId) => `${QUIZ_EXERCISES}/${exerciseId}`;
export const COURSES = '/courses';
export const USERS = '/users';
export const COURSE = (courseId) => `${COURSES}/${courseId}`;
export const COURSE_STUDENTS = (courseId, username) => `${COURSES}/${courseId}/students/${username}`;
export const COURSE_TUTORS = (courseId, username) => `${COURSES}/${courseId}/tutors/${username}`;
export const COURSE_INSTRUCTORS = (courseId, username) => `${COURSES}/${courseId}/instructors/${username}`;
export const EXERCISES = (courseId) => `${COURSE(courseId)}/exercises`;
export const EXERCISE = (courseId, exerciseId) => `${EXERCISES(courseId)}/${exerciseId}`;
export const PARTICIPATION = (exerciseId) => `/exercises/${exerciseId}/participation`;
export const PARTICIPATIONS = (courseId, exerciseId) => `${EXERCISE(courseId, exerciseId)}/participations`;
export const COMMIT = (participationId) => `/repository/${participationId}/commit`;
export const NEW_FILE = (participationId) => `/repository/${participationId}/file`;
export const PARTICIPATION_WITH_RESULT = (participationId) => `/participations/${participationId}/withLatestResult`;
export const SUBMIT_QUIZ_LIVE = (exerciseId) => `/exercises/${exerciseId}/submissions/live`;
export const SUBMIT_QUIZ_EXAM = (exerciseId) => `/exercises/${exerciseId}/submissions/exam`;
export const EXAMS = (courseId) => `${COURSE(courseId)}/exams`;
export const EXAM = (courseId, examId) => EXAMS(courseId) + `/${examId}`;
export const EXERCISE_GROUPS = (courseId, examId) => `${EXAM(courseId, examId)}/exerciseGroups`;
export const TEXT_EXERCISE = '/text-exercises';
export const EXAM_STUDENTS = (courseId, examId, username) => `${EXAM(courseId, examId)}/students/${username}`;
export const GENERATE_STUDENT_EXAMS = (courseId, examId) => `${EXAM(courseId, examId)}/generate-student-exams`;
export const START_EXERCISES = (courseId, examId) => `${EXAM(courseId, examId)}/student-exams/start-exercises`;
export const EXAM_CONDUCTION = (courseId, examId) => `${EXAM(courseId, examId)}/studentExams/conduction`;
export const SUBMIT_TEXT_EXAM = (exerciseId) => `/exercises/${exerciseId}/text-submissions`;
