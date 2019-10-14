export const PROGRAMMING_EXERCISES_SETUP = "/programming-exercises/setup";
export const PROGRAMMING_EXERCISES = "/programming-exercises";
export const PROGRAMMING_EXERCISE = (exerciseId) => `${PROGRAMMING_EXERCISES}/${exerciseId}`;
export const COURSES = "/courses";
export const COURSE = (courseId) => `${COURSES}/${courseId}`;
export const EXERCISES = (courseId) => `${COURSE(courseId)}/exercises`;
export const EXERCISE = (courseId, exerciseId) => `${EXERCISES(courseId)}/${exerciseId}`;
export const PARTICIPATIONS = (courseId, exerciseId) => `${EXERCISE(courseId, exerciseId)}/participations`;
export const COMMIT = (participationId) => `/repository/${participationId}/commit`;