import { ArtemisPageobjects } from './pageobjects/ArtemisPageobjects';
import { ArtemisRequests } from './requests/ArtemisRequests';

/**
 * File which contains all shared code related to testing Artemis.
 */

// Requests
const requests = new ArtemisRequests();
export const courseManagementRequest = requests.courseManagement;

// PageObjects
const pageObjects = new ArtemisPageobjects();

export const loginPage = pageObjects.login;

export const courseCreation = pageObjects.course.creation;
export const courseList = pageObjects.course.list;
export const courseOverview = pageObjects.course.overview;
export const courseManagement = pageObjects.course.management;
export const courseManagementExercises = pageObjects.course.managementExercises;
export const courseCommunication = pageObjects.course.communication;
export const courseMessages = pageObjects.course.messages;
export const courseAssessment = pageObjects.assessment.course;

export const lectureCreation = pageObjects.lecture.creation;
export const lectureManagement = pageObjects.lecture.management;

export const navigationBar = pageObjects.navigationBar;

export const examCreation = pageObjects.exam.creation;
export const examManagement = pageObjects.exam.management;
export const examDetails = pageObjects.exam.details;
export const examAssessment = pageObjects.assessment.exam;
export const examNavigation = pageObjects.exam.navigationBar;
export const examStartEnd = pageObjects.exam.startEnd;
export const examExerciseGroupCreation = pageObjects.exam.exerciseGroupCreation;
export const examExerciseGroups = pageObjects.exam.exerciseGroups;
export const examParticipation = pageObjects.exam.participation;
export const examTestRun = pageObjects.exam.testRun;
export const studentExamManagement = pageObjects.exam.studentExamManagement;

export const studentAssessment = pageObjects.assessment.student;

export const exerciseAssessment = pageObjects.assessment.exercise;
export const exerciseResult = pageObjects.exercise.result;

export const textExerciseCreation = pageObjects.exercise.text.creation;
export const textExerciseEditor = pageObjects.exercise.text.editor;
export const textExerciseAssessment = pageObjects.assessment.text;
export const textExerciseFeedback = pageObjects.exercise.text.feedback;
export const textExerciseExampleSubmissionCreation = pageObjects.exercise.text.exampleSubmissionCreation;
export const textExerciseExampleSubmissions = pageObjects.exercise.text.exampleSubmissions;

export const modelingExerciseCreation = pageObjects.exercise.modeling.creation;
export const modelingExerciseEditor = pageObjects.exercise.modeling.editor;
export const modelingExerciseAssessment = pageObjects.assessment.modeling;
export const modelingExerciseFeedback = pageObjects.exercise.modeling.feedback;

export const programmingExerciseCreation = pageObjects.exercise.programming.creation;
export const programmingExerciseEditor = pageObjects.exercise.programming.editor;
export const programmingExerciseAssessment = pageObjects.assessment.programming;
export const programmingExerciseFeedback = pageObjects.exercise.programming.feedback;
export const programmingExercisesScaConfig = pageObjects.exercise.programming.scaConfiguration;
export const programmingExerciseScaFeedback = pageObjects.exercise.programming.scaFeedback;

export const quizExerciseCreation = pageObjects.exercise.quiz.creation;
export const quizExerciseMultipleChoice = pageObjects.exercise.quiz.multipleChoice;
export const quizExerciseShortAnswerQuiz = pageObjects.exercise.quiz.shortAnswer;
export const quizExerciseDragAndDropQuiz = pageObjects.exercise.quiz.dragAndDrop;

export const fileUploadExerciseCreation = pageObjects.exercise.fileUpload.creation;
export const fileUploadExerciseEditor = pageObjects.exercise.fileUpload.editor;
export const fileUploadExerciseAssessment = pageObjects.assessment.fileUpload;
export const fileUploadExerciseFeedback = pageObjects.exercise.fileUpload.feedback;
