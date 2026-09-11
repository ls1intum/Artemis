import type { StudentQuizParticipationWithSolutions } from "./student-quiz-participation-with-solutions";
import type { StudentQuizParticipationWithQuestions } from "./student-quiz-participation-with-questions";
import type { StudentQuizParticipationWithoutQuestions } from "./student-quiz-participation-without-questions";

export type StudentQuizParticipation = StudentQuizParticipationWithSolutions | StudentQuizParticipationWithQuestions | StudentQuizParticipationWithoutQuestions;
