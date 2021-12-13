import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { CourseScoreCalculationService, ScoreType } from 'app/overview/course-score-calculation.service';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Course } from 'app/entities/course.model';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import dayjs from 'dayjs';
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
        const result = courseScoreCalculationService.calculateTotalScores(course.exercises, course);
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
        const result = courseScoreCalculationService.calculateTotalScores(course.exercises, course);
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
        const result = courseScoreCalculationService.calculateTotalScores(course.exercises, course);
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
        const result = courseScoreCalculationService.calculateTotalScores(course.exercises, course);
        expectCalculationResult(result, 30, 300, 300, 10, 0, 10);
    });

    it('should pick the last result for calculation of the course score if multiple exits', () => {
        const course = new Course();
        course.exercises = [];
        const exercise1 = addTextExerciseToCourse(course, 10, 10, IncludedInOverallScore.INCLUDED_COMPLETELY, true);
        const participation = new StudentParticipation(ParticipationType.STUDENT);
        participation.exercise = exercise1;
        participation.initializationDate = dayjs();
        participation.results = [];
        exercise1.studentParticipations!.push(participation);

        const oldResult = new Result();
        oldResult.completionDate = dayjs(exercise1.dueDate!).subtract(1, 'days');
        oldResult.participation = participation;
        oldResult.score = 200;
        oldResult.rated = true;
        participation.results.push(oldResult);

        const newResult = new Result();
        newResult.completionDate = dayjs(oldResult.completionDate).add(1, 'hours');
        newResult.participation = participation;
        newResult.score = 0;
        newResult.rated = true;
        participation.results.push(newResult);

        const result = courseScoreCalculationService.calculateTotalScores(course.exercises, course);
        expectCalculationResult(result, 0, 0, 0, 10, 0, 10);
    });

    function addParticipationAndResultToExercise(exercise: Exercise, finishedInTime: boolean, scoreAwarded: number, rated: boolean) {
        const participation = new StudentParticipation(ParticipationType.STUDENT);
        participation.exercise = exercise;
        participation.initializationDate = dayjs();
        participation.results = [];
        exercise.studentParticipations!.push(participation);

        const result = new Result();
        if (finishedInTime) {
            result.completionDate = dayjs(exercise.dueDate!).subtract(1, 'days');
        } else {
            result.completionDate = dayjs(exercise.dueDate!).add(1, 'days');
        }
        result.participation = participation;
        result.score = scoreAwarded;
        result.rated = rated;
        participation.results.push(result);
    }

    function addTextExerciseToCourse(course: Course, maxPoints: number, bonusPoints: number, includedInOverallScore: IncludedInOverallScore, isFinished: boolean) {
        const exercise = new TextExercise(course, undefined);
        exercise.includedInOverallScore = includedInOverallScore;
        exercise.maxPoints = maxPoints;
        exercise.bonusPoints = bonusPoints;
        exercise.studentParticipations = [];
        if (isFinished) {
            exercise.dueDate = dayjs().subtract(1, 'days');
        } else {
            exercise.dueDate = dayjs().add(1, 'days');
        }
        course.exercises!.push(exercise);
        return exercise;
    }

    function expectCalculationResult(
        resultMap: Map<string, number>,
        expectedAbsoluteScore?: number,
        expectedRelativeScore?: number,
        expectedCurrentRelativeScore?: number,
        expectedMaxPoints?: number,
        expectedPresentationScore?: number,
        expectedReachableScore?: number,
    ) {
        if (expectedAbsoluteScore) {
            expect(resultMap.get(ScoreType.ABSOLUTE_SCORE)).to.equal(expectedAbsoluteScore);
        }
        if (expectedRelativeScore) {
            expect(resultMap.get(ScoreType.RELATIVE_SCORE)).to.equal(expectedRelativeScore);
        }
        if (expectedCurrentRelativeScore) {
            expect(resultMap.get(ScoreType.MAX_POINTS)).to.equal(expectedMaxPoints);
        }
        if (expectedPresentationScore) {
            expect(resultMap.get(ScoreType.PRESENTATION_SCORE)).to.equal(expectedPresentationScore);
        }
        if (expectedReachableScore) {
            expect(resultMap.get(ScoreType.REACHABLE_POINTS)).to.equal(expectedReachableScore);
        }
    }
});
