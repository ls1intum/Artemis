import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';
import { ArtemisTestModule } from '../test.module';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerSpot, SpotType } from 'app/entities/quiz/short-answer-spot.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { cloneDeep } from 'lodash-es';

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
        const spotNr1 = service.getSpotNr('[-spot 123]');
        const spotNr2 = service.getSpotNr('[-spot-number 456]');

        expect(solutions).toContain(solution);
        expect(spots).toContain(spot);
        expect(sampleSolutions).toContain(solution);
        expect(mappingFromGetter).toEqual(mapping);
        expect(spotFromGetter).toEqual(spot);
        expect(spotNr1).toBe(123);
        expect(spotNr2).toBe(456);

        let isMappedTogether = service.isMappedTogether(shortAnswerQuestion.correctMappings, solution, spot);
        expect(isMappedTogether).toBeTrue();

        isMappedTogether = service.isMappedTogether(shortAnswerQuestion.correctMappings, solutionUnmapped, spot);
        expect(isMappedTogether).toBeFalse();

        let isInputField = service.isInputField('[-spot 123]');
        expect(isInputField).toBeTrue();

        isInputField = service.isInputField('[-spot-number 456]');
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

    it('should check for invalid number spot solution', () => {
        const spot1 = new ShortAnswerSpot();
        spot1.type = SpotType.NUMBER;

        const solution1 = new ShortAnswerSolution();
        const solution2 = new ShortAnswerSolution();
        const solution3 = new ShortAnswerSolution();
        const solution4 = new ShortAnswerSolution();
        solution1.text = '1.1-5.9';
        solution2.text = '-10.1-12.1';
        solution3.text = '-100--90.1';
        solution4.text = '-8.2';

        const mapping1 = new ShortAnswerMapping(spot1, solution1);
        const mapping2 = new ShortAnswerMapping(spot1, solution2);
        const mapping3 = new ShortAnswerMapping(spot1, solution3);
        const mapping4 = new ShortAnswerMapping(spot1, solution4);

        expect(service.everyNumberSpotHasValidSolution([mapping1, mapping2, mapping3, mapping4], [spot1])).toBeTrue();

        const solution5 = new ShortAnswerSolution();
        solution5.text = 'a';
        const mapping5 = new ShortAnswerMapping(spot1, solution5);
        expect(service.everyNumberSpotHasValidSolution([mapping5], [spot1])).toBeFalse();

        const solution6 = new ShortAnswerSolution();
        solution6.text = '';
        const mapping6 = new ShortAnswerMapping(spot1, solution6);
        expect(service.everyNumberSpotHasValidSolution([mapping6], [spot1])).toBeFalse();

        const solution7 = new ShortAnswerSolution();
        solution7.text = '-1.1--10.1';
        const mapping7 = new ShortAnswerMapping(spot1, solution7);
        expect(service.everyNumberSpotHasValidSolution([mapping7], [spot1])).toBeFalse();

        const solution8 = new ShortAnswerSolution();
        solution8.text = 'x--10.1';
        const mapping8 = new ShortAnswerMapping(spot1, solution8);
        expect(service.everyNumberSpotHasValidSolution([mapping8], [spot1])).toBeFalse();

        const solution9 = new ShortAnswerSolution();
        solution9.text = '-1.1-a';
        const mapping9 = new ShortAnswerMapping(spot1, solution9);
        expect(service.everyNumberSpotHasValidSolution([mapping9], [spot1])).toBeFalse();

        expect(service.everyNumberSpotHasValidSolution([mapping1, mapping2, mapping3, mapping4, mapping5], [spot1])).toBeFalse();
    });
});
