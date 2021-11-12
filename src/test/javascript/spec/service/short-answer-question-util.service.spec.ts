import { TestBed, getTestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';
import { ArtemisTestModule } from '../test.module';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { cloneDeep } from 'lodash-es';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ShortAnswerQuestionUtil', () => {
    let injector: TestBed;
    let service: ShortAnswerQuestionUtil;
    let markdownService: ArtemisMarkdownService;

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
            expect(hasNoMisleadingMapping).to.be.true;
        } else {
            expect(hasNoMisleadingMapping).to.be.false;
        }
    };

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule],
        });

        injector = getTestBed();
        service = injector.get(ShortAnswerQuestionUtil);
        markdownService = injector.get(ArtemisMarkdownService);
    });
    it('should return correct getter', () => {
        const solutions = service.getAllSolutionsForSpot(shortAnswerQuestion.correctMappings, spot);
        const spots = service.getAllSpotsForSolutions(shortAnswerQuestion.correctMappings, solution);
        const sampleSolutions = service.getSampleSolutions(shortAnswerQuestion);
        const mappingFromGetter = service.getShortAnswerMapping(shortAnswerQuestion.correctMappings, solution, spot);
        const spotFromGetter = service.getSpot(spot.spotNr!, shortAnswerQuestion);
        const spotNr = service.getSpotNr('[-spot 123]');

        expect(solutions).to.contain(solution);
        expect(spots).to.be.contain(spot);
        expect(sampleSolutions).to.be.contain(solution);
        expect(mappingFromGetter).to.be.equal(mapping);
        expect(spotFromGetter).to.be.equal(spot);
        expect(spotNr).to.be.equal(123);

        let isMappedTogether = service.isMappedTogether(shortAnswerQuestion.correctMappings, solution, spot);
        expect(isMappedTogether).to.be.true;

        isMappedTogether = service.isMappedTogether(shortAnswerQuestion.correctMappings, solutionUnmapped, spot);
        expect(isMappedTogether).to.be.false;

        let isInputField = service.isInputField('[-spot 123]');
        expect(isInputField).to.be.true;

        isInputField = service.isInputField('text with no input');
        expect(isInputField).to.be.false;

        let isSameSetOfSpots = service.isSameSetOfSpots([spot], [spot]);
        expect(isSameSetOfSpots).to.be.true;

        isSameSetOfSpots = service.isSameSetOfSpots([spot], [spotUnmapped]);
        expect(isSameSetOfSpots).to.be.false;

        let isSameSolution = service.isSameSolution(solution, solution);
        expect(isSameSolution).to.be.true;

        isSameSolution = service.isSameSolution(solution, solutionUnmapped);
        expect(isSameSolution).to.be.false;

        let isSameSpot = service.isSameSpot(spot, spot);
        expect(isSameSpot).to.be.true;

        isSameSpot = service.isSameSpot(spot, spotUnmapped);
        expect(isSameSpot).to.be.false;

        let areAllSolutionsinSampleSolution = service.allSolutionsAreInSampleSolution(solutions, sampleSolutions);
        expect(areAllSolutionsinSampleSolution).to.be.true;

        areAllSolutionsinSampleSolution = service.allSolutionsAreInSampleSolution([solutionUnmapped], sampleSolutions);
        expect(areAllSolutionsinSampleSolution).to.be.false;
    });

    it('should check whether spots and solutions are setup correctly', () => {
        let mappedSolutionsHaveSpots = service.everyMappedSolutionHasASpot(shortAnswerQuestion.correctMappings!);
        expect(mappedSolutionsHaveSpots).to.be.true;

        const wrongMapping = cloneDeep(shortAnswerQuestion.correctMappings);
        // @ts-ignore
        wrongMapping.forEach((m) => (m.spot = undefined));
        mappedSolutionsHaveSpots = service.everyMappedSolutionHasASpot(wrongMapping!);
        expect(mappedSolutionsHaveSpots).to.be.false;

        let spotsHaveSolutions = service.everySpotHasASolution(shortAnswerQuestion.correctMappings!, shortAnswerQuestion.spots!);
        expect(spotsHaveSolutions).to.be.true;

        spotsHaveSolutions = service.everySpotHasASolution(shortAnswerQuestion.correctMappings!, [spotUnmapped]);
        expect(spotsHaveSolutions).to.be.false;

        let hasDuplicatedMappings = service.hasMappingDuplicateValues(shortAnswerQuestion.correctMappings!);
        expect(hasDuplicatedMappings).to.be.false;

        const duplicatedMapping = cloneDeep(shortAnswerQuestion.correctMappings!);
        duplicatedMapping.push(duplicatedMapping[0]);
        hasDuplicatedMappings = service.hasMappingDuplicateValues(duplicatedMapping);
        expect(hasDuplicatedMappings).to.be.true;

        let hasAtLeastAsManySolutionsAsSpots = service.atLeastAsManySolutionsAsSpots(shortAnswerQuestion);
        expect(hasAtLeastAsManySolutionsAsSpots).to.be.true;

        const faultyShortAnswerQuestion = new ShortAnswerQuestion();
        faultyShortAnswerQuestion.spots = [spot, spotUnmapped];
        faultyShortAnswerQuestion.solutions = [solution];
        hasAtLeastAsManySolutionsAsSpots = service.atLeastAsManySolutionsAsSpots(faultyShortAnswerQuestion);
        expect(hasAtLeastAsManySolutionsAsSpots).to.be.false;

        let hasNoMisleadingMapping = service.validateNoMisleadingShortAnswerMapping(shortAnswerQuestion);
        expect(hasNoMisleadingMapping).to.be.true;
        // @ts-ignore
        shortAnswerQuestion.correctMappings = undefined;
        hasNoMisleadingMapping = service.validateNoMisleadingShortAnswerMapping(shortAnswerQuestion);
        expect(hasNoMisleadingMapping).to.be.true;
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
        expect(textParts[0][0]).to.contain(textPart1);
        expect(textParts[1][0]).to.contain(textPart2);

        const textPartsInHTML = service.transformTextPartsIntoHTML(textParts);
        expect(textPartsInHTML[0][0]).to.contain(`<p>${textPart1}</p>`);
        expect(textPartsInHTML[1][0]).to.contain(`<p><strong>${textPart2.split('**').join('')}</strong></p>`);
    });
});
