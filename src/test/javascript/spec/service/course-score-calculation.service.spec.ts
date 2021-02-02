import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Course } from 'app/entities/course.model';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import * as moment from 'moment';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseScoreCalculationService', () => {
    let courseScoreCalculationService: CourseScoreCalculationService;

    beforeEach(() => {
        courseScoreCalculationService = new CourseScoreCalculationService();
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should return the correctly calculate the course score for all normal exercise', () => {
        const course = new Course();
        course.exercises = [];
        const exercise1 = addTextExerciseToCourse(course, 10, 10, IncludedInOverallScore.INCLUDED_COMPLETELY, true);
        const exercise2 = addTextExerciseToCourse(course, 10, 10, IncludedInOverallScore.INCLUDED_COMPLETELY, true);
        const exercise3 = addTextExerciseToCourse(course, 10, 10, IncludedInOverallScore.INCLUDED_COMPLETELY, true);
        const exercise4 = addTextExerciseToCourse(course, 10, 10, IncludedInOverallScore.INCLUDED_COMPLETELY, true);
        addParticipationAndResultToExercise(exercise1, true, 200, true);
        addParticipationAndResultToExercise(exercise2, true, 0, true);
        addParticipationAndResultToExercise(exercise3, true, 100, true);
        addParticipationAndResultToExercise(exercise4, true, 50, true);
        const result = courseScoreCalculationService.calculateTotalScores(course.exercises);
        expectCalculationResult(result, 35, 87.5, 87.5, 40, 0, 40);
    });

    it('should return the correctly calculate the course score for all bonus exercise', () => {
        const course = new Course();
        course.exercises = [];
        const exercise1 = addTextExerciseToCourse(course, 10, 0, IncludedInOverallScore.INCLUDED_AS_BONUS, true);
        const exercise2 = addTextExerciseToCourse(course, 10, 0, IncludedInOverallScore.INCLUDED_AS_BONUS, true);
        const exercise3 = addTextExerciseToCourse(course, 10, 0, IncludedInOverallScore.INCLUDED_AS_BONUS, true);
        const exercise4 = addTextExerciseToCourse(course, 10, 0, IncludedInOverallScore.INCLUDED_AS_BONUS, true);
        addParticipationAndResultToExercise(exercise1, true, 100, true);
        addParticipationAndResultToExercise(exercise2, true, 0, true);
        addParticipationAndResultToExercise(exercise3, true, 100, true);
        addParticipationAndResultToExercise(exercise4, true, 50, true);
        const result = courseScoreCalculationService.calculateTotalScores(course.exercises);
        expectCalculationResult(result, 25, 0, 0, 0, 0, 0);
    });

    it('should return the correctly calculate the course score for all not included exercise', () => {
        const course = new Course();
        course.exercises = [];
        const exercise1 = addTextExerciseToCourse(course, 10, 0, IncludedInOverallScore.NOT_INCLUDED, true);
        const exercise2 = addTextExerciseToCourse(course, 10, 0, IncludedInOverallScore.NOT_INCLUDED, true);
        const exercise3 = addTextExerciseToCourse(course, 10, 0, IncludedInOverallScore.NOT_INCLUDED, true);
        const exercise4 = addTextExerciseToCourse(course, 10, 0, IncludedInOverallScore.NOT_INCLUDED, true);
        addParticipationAndResultToExercise(exercise1, true, 100, true);
        addParticipationAndResultToExercise(exercise2, true, 0, true);
        addParticipationAndResultToExercise(exercise3, true, 100, true);
        addParticipationAndResultToExercise(exercise4, true, 50, true);
        const result = courseScoreCalculationService.calculateTotalScores(course.exercises);
        expectCalculationResult(result, 0, 0, 0, 0, 0, 0);
    });

    it('should return the correctly calculate the course score for a mixture of calculation types', () => {
        const course = new Course();
        course.exercises = [];
        const exercise1 = addTextExerciseToCourse(course, 10, 10, IncludedInOverallScore.INCLUDED_COMPLETELY, true);
        const exercise2 = addTextExerciseToCourse(course, 10, 0, IncludedInOverallScore.INCLUDED_AS_BONUS, true);
        const exercise3 = addTextExerciseToCourse(course, 10, 0, IncludedInOverallScore.NOT_INCLUDED, true);
        addParticipationAndResultToExercise(exercise1, true, 200, true);
        addParticipationAndResultToExercise(exercise2, true, 100, true);
        addParticipationAndResultToExercise(exercise3, true, 100, true);
        const result = courseScoreCalculationService.calculateTotalScores(course.exercises);
        expectCalculationResult(result, 30, 300, 300, 10, 0, 10);
    });

    it('should pick the last result for calculation of the course score if multiple exits', () => {
        const course = new Course();
        course.exercises = [];
        const exercise1 = addTextExerciseToCourse(course, 10, 10, IncludedInOverallScore.INCLUDED_COMPLETELY, true);
        const participation = new StudentParticipation(ParticipationType.STUDENT);
        participation.exercise = exercise1;
        participation.initializationDate = moment();
        participation.results = [];
        exercise1.studentParticipations!.push(participation);

        const oldResult = new Result();
        oldResult.completionDate = moment(exercise1.dueDate!).subtract(1, 'days');
        oldResult.participation = participation;
        oldResult.score = 200;
        oldResult.rated = true;
        participation.results.push(oldResult);

        const newResult = new Result();
        newResult.completionDate = moment(oldResult.completionDate).add(1, 'hours');
        newResult.participation = participation;
        newResult.score = 0;
        newResult.rated = true;
        participation.results.push(newResult);

        const result = courseScoreCalculationService.calculateTotalScores(course.exercises);
        expectCalculationResult(result, 0, 0, 0, 10, 0, 10);
    });

    function addParticipationAndResultToExercise(exercise: Exercise, finishedInTime: boolean, scoreAwarded: number, rated: boolean) {
        const participation = new StudentParticipation(ParticipationType.STUDENT);
        participation.exercise = exercise;
        participation.initializationDate = moment();
        participation.results = [];
        exercise.studentParticipations!.push(participation);

        const result = new Result();
        if (finishedInTime) {
            result.completionDate = moment(exercise.dueDate!).subtract(1, 'days');
        } else {
            result.completionDate = moment(exercise.dueDate!).add(1, 'days');
        }
        result.participation = participation;
        result.score = scoreAwarded;
        result.rated = rated;
        participation.results.push(result);
    }

    function addTextExerciseToCourse(course: Course, maxScore: number, bonusPoints: number, includedInOverallScore: IncludedInOverallScore, isFinished: boolean) {
        const exercise = new TextExercise(course, undefined);
        exercise.includedInOverallScore = includedInOverallScore;
        exercise.maxPoints = maxScore;
        exercise.bonusPoints = bonusPoints;
        exercise.studentParticipations = [];
        if (isFinished) {
            exercise.dueDate = moment().subtract(1, 'days');
        } else {
            exercise.dueDate = moment().add(1, 'days');
        }
        course.exercises!.push(exercise);
        return exercise;
    }

    function expectCalculationResult(
        resultMap: Map<String, number>,
        expectedAbsoluteScore?: number,
        expectedRelativeScore?: number,
        expectedCurrentRelativeScore?: number,
        expectedMaxScore?: number,
        expectedPresentationScore?: number,
        expectedReachableScore?: number,
    ) {
        if (expectedAbsoluteScore) {
            expect(resultMap.get('absoluteScore')).to.equal(expectedAbsoluteScore);
        }
        if (expectedRelativeScore) {
            expect(resultMap.get('relativeScore')).to.equal(expectedRelativeScore);
        }
        if (expectedCurrentRelativeScore) {
            expect(resultMap.get('maxScore')).to.equal(expectedMaxScore);
        }
        if (expectedPresentationScore) {
            expect(resultMap.get('presentationScore')).to.equal(expectedPresentationScore);
        }
        if (expectedReachableScore) {
            expect(resultMap.get('reachableScore')).to.equal(expectedReachableScore);
        }
    }
});
