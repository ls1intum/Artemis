import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';
import { cloneDeep } from 'lodash-es';
import { ArtemisTestModule } from '../test.module';

describe('ShortAnswerQuestionUtil', () => {
    let service: ShortAnswerQuestionUtil;

    const spot = new ShortAnswerSpot();
    spot.spotNr = 1;
    spot.id = 1;
    const solution = new ShortAnswerSolution();
    solution.text = 'Solution 1';
    solution.id = 1;
    const mapping = new ShortAnswerMapping(spot, solution);
    const shortAnswerQuestion: ShortAnswerQuestion = new ShortAnswerQuestion();
    shortAnswerQuestion.spots = [spot];
    shortAnswerQuestion.solutions = [solution];
    shortAnswerQuestion.text = `This is a short answer question
    **with markdown**`;
    shortAnswerQuestion.correctMappings = [mapping];

    const spotUnmapped = new ShortAnswerSpot();
    spotUnmapped.spotNr = 2;
    spotUnmapped.id = 2;
    const solutionUnmapped = new ShortAnswerSolution();
    solutionUnmapped.text = 'Solution 2';
    solutionUnmapped.id = 2;

    const addMappingToQuestionAndCheckMisleadingMapping = (spotForMapping: ShortAnswerSpot, solutionForMapping: ShortAnswerSolution, toBeTrue: boolean) => {
        const mappingToCheck = new ShortAnswerMapping(spotForMapping, solutionForMapping);
        shortAnswerQuestion.correctMappings!.push(mappingToCheck);
        const hasNoMisleadingMapping = service.validateNoMisleadingShortAnswerMapping(shortAnswerQuestion);
        if (toBeTrue) {
            expect(hasNoMisleadingMapping).toBeTrue();
        } else {
            expect(hasNoMisleadingMapping).toBeFalse();
        }
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule],
        });

        service = TestBed.inject(ShortAnswerQuestionUtil);
    });
    it('should return correct getter', () => {
        const solutions = service.getAllSolutionsForSpot(shortAnswerQuestion.correctMappings, spot);
        const spots = service.getAllSpotsForSolutions(shortAnswerQuestion.correctMappings, solution);
        const sampleSolutions = service.getSampleSolutions(shortAnswerQuestion);
        const mappingFromGetter = service.getShortAnswerMapping(shortAnswerQuestion.correctMappings, solution, spot);
        const spotFromGetter = service.getSpot(spot.spotNr!, shortAnswerQuestion);
        const spotNr = service.getSpotNr('[-spot 123]');

        expect(solutions).toContain(solution);
        expect(spots).toContain(spot);
        expect(sampleSolutions).toContain(solution);
        expect(mappingFromGetter).toEqual(mapping);
        expect(spotFromGetter).toEqual(spot);
        expect(spotNr).toBe(123);

        let isMappedTogether = service.isMappedTogether(shortAnswerQuestion.correctMappings, solution, spot);
        expect(isMappedTogether).toBeTrue();

        isMappedTogether = service.isMappedTogether(shortAnswerQuestion.correctMappings, solutionUnmapped, spot);
        expect(isMappedTogether).toBeFalse();

        let isInputField = service.isInputField('[-spot 123]');
        expect(isInputField).toBeTrue();

        isInputField = service.isInputField('text with no input');
        expect(isInputField).toBeFalse();

        let isSameSetOfSpots = service.isSameSetOfSpots([spot], [spot]);
        expect(isSameSetOfSpots).toBeTrue();

        isSameSetOfSpots = service.isSameSetOfSpots([spot], [spotUnmapped]);
        expect(isSameSetOfSpots).toBeFalse();

        let isSameSolution = service.isSameSolution(solution, solution);
        expect(isSameSolution).toBeTrue();

        isSameSolution = service.isSameSolution(solution, solutionUnmapped);
        expect(isSameSolution).toBeFalse();

        let isSameSpot = service.isSameSpot(spot, spot);
        expect(isSameSpot).toBeTrue();

        isSameSpot = service.isSameSpot(spot, spotUnmapped);
        expect(isSameSpot).toBeFalse();

        let areAllSolutionsinSampleSolution = service.allSolutionsAreInSampleSolution(solutions, sampleSolutions);
        expect(areAllSolutionsinSampleSolution).toBeTrue();

        areAllSolutionsinSampleSolution = service.allSolutionsAreInSampleSolution([solutionUnmapped], sampleSolutions);
        expect(areAllSolutionsinSampleSolution).toBeFalse();
    });

    it('should check whether spots and solutions are setup correctly', () => {
        let mappedSolutionsHaveSpots = service.everyMappedSolutionHasASpot(shortAnswerQuestion.correctMappings!);
        expect(mappedSolutionsHaveSpots).toBeTrue();

        const wrongMapping = cloneDeep(shortAnswerQuestion.correctMappings);
        // @ts-ignore
        wrongMapping.forEach((m) => (m.spot = undefined));
        mappedSolutionsHaveSpots = service.everyMappedSolutionHasASpot(wrongMapping!);
        expect(mappedSolutionsHaveSpots).toBeFalse();

        let spotsHaveSolutions = service.everySpotHasASolution(shortAnswerQuestion.correctMappings!, shortAnswerQuestion.spots!);
        expect(spotsHaveSolutions).toBeTrue();

        spotsHaveSolutions = service.everySpotHasASolution(shortAnswerQuestion.correctMappings!, [spotUnmapped]);
        expect(spotsHaveSolutions).toBeFalse();

        let hasDuplicatedMappings = service.hasMappingDuplicateValues(shortAnswerQuestion.correctMappings!);
        expect(hasDuplicatedMappings).toBeFalse();

        const duplicatedMapping = cloneDeep(shortAnswerQuestion.correctMappings!);
        duplicatedMapping.push(duplicatedMapping[0]);
        hasDuplicatedMappings = service.hasMappingDuplicateValues(duplicatedMapping);
        expect(hasDuplicatedMappings).toBeTrue();

        let hasAtLeastAsManySolutionsAsSpots = service.atLeastAsManySolutionsAsSpots(shortAnswerQuestion);
        expect(hasAtLeastAsManySolutionsAsSpots).toBeTrue();

        const faultyShortAnswerQuestion = new ShortAnswerQuestion();
        faultyShortAnswerQuestion.spots = [spot, spotUnmapped];
        faultyShortAnswerQuestion.solutions = [solution];
        hasAtLeastAsManySolutionsAsSpots = service.atLeastAsManySolutionsAsSpots(faultyShortAnswerQuestion);
        expect(hasAtLeastAsManySolutionsAsSpots).toBeFalse();

        let hasNoMisleadingMapping = service.validateNoMisleadingShortAnswerMapping(shortAnswerQuestion);
        expect(hasNoMisleadingMapping).toBeTrue();
        // @ts-ignore
        shortAnswerQuestion.correctMappings = undefined;
        hasNoMisleadingMapping = service.validateNoMisleadingShortAnswerMapping(shortAnswerQuestion);
        expect(hasNoMisleadingMapping).toBeTrue();
    });

    it('should check for misleading mappings', () => {
        // This is done as the correctMappings is undefined (see previous test)
        shortAnswerQuestion.correctMappings = [mapping];

        const spot2 = new ShortAnswerSpot();
        spot2.spotNr = 2;
        spot2.id = 2;
        const spot3 = new ShortAnswerSpot();
        spot3.spotNr = 3;
        spot3.id = 3;

        const solution2 = new ShortAnswerSolution();
        solution2.text = 'Solution 2';
        solution2.id = 2;
        const solution3 = new ShortAnswerSolution();
        solution3.text = 'Solution 3';
        solution3.id = 3;

        shortAnswerQuestion.spots!.push(spot2, spot3);
        shortAnswerQuestion.solutions!.push(solution2, solution3);

        addMappingToQuestionAndCheckMisleadingMapping(spot2, solution2, false);

        addMappingToQuestionAndCheckMisleadingMapping(spot3, solution3, true);

        addMappingToQuestionAndCheckMisleadingMapping(spot2, solution, false);

        addMappingToQuestionAndCheckMisleadingMapping(spot, solution2, true);

        addMappingToQuestionAndCheckMisleadingMapping(spot, solution3, false);

        addMappingToQuestionAndCheckMisleadingMapping(spot2, solution3, false);

        addMappingToQuestionAndCheckMisleadingMapping(spot3, solution, false);

        addMappingToQuestionAndCheckMisleadingMapping(spot3, solution2, true);

        const solution4 = new ShortAnswerSolution();
        solution4.text = 'Solution 4';
        solution4.id = 4;
        addMappingToQuestionAndCheckMisleadingMapping(spot, solution4, true);
    });

    it('should split the question text into text parts and transform to html', () => {
        const textPart1 = 'This is a short answer question';
        const textPart2 = '**with highlighted markdown**';
        shortAnswerQuestion.text = textPart1 + '\n' + textPart2;
        const textParts = service.divideQuestionTextIntoTextParts(shortAnswerQuestion.text!);
        expect(textParts[0][0]).toContain(textPart1);
        expect(textParts[1][0]).toContain(textPart2);

        const textPartsInHTML = service.transformTextPartsIntoHTML(textParts);
        expect(textPartsInHTML[0][0]).toContain(`<p>${textPart1}</p>`);
        expect(textPartsInHTML[1][0]).toContain(`<p><strong>${textPart2.split('**').join('')}</strong></p>`);
    });
});
