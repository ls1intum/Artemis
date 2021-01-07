import { Injectable } from '@angular/core';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';

@Injectable({ providedIn: 'root' })
export class ShortAnswerQuestionUtil {
    constructor() {}

    /**
     * Get a sample solution for the given short answer question
     *
     * @param question {object} the short answer question we want to solve
     * @param [mappings] {Array} (optional) the mappings we try to use in the sample solution (this may contain incorrect mappings - they will be filtered out)
     * @return {Array} array of mappings that would solve this question (may be empty, if question is unsolvable)
     */
    solveShortAnswer(question: ShortAnswerQuestion, mappings?: ShortAnswerMapping[]): ShortAnswerMapping[] {
        if (!question.correctMappings) {
            return [];
        }

        const sampleMappings = new Array<ShortAnswerMapping>();
        let availableSolutions = question.solutions;

        // filter out spots that do not need to be mapped
        let remainingSpots: ShortAnswerSpot[] = question.spots!.filter((spot) => {
            return question.correctMappings!.some((mapping) => {
                return this.isSameSpot(mapping.spot, spot);
            }, this);
        }, this);

        if (remainingSpots.length !== question.spots!.length) {
            return [];
        }

        if (mappings) {
            // add mappings that are already correct
            mappings.forEach((mapping) => {
                const correctMapping = this.getShortAnswerMapping(question.correctMappings!, mapping.solution!, mapping.spot!);
                if (correctMapping) {
                    sampleMappings.push(correctMapping);
                    remainingSpots = remainingSpots.filter((spot) => {
                        return !this.isSameSpot(spot, mapping.spot);
                    }, this);
                    availableSolutions = availableSolutions?.filter((solution) => {
                        return !this.isSameSolution(solution, mapping.solution);
                    }, this);
                }
            }, this);
        }

        // solve recursively
        const solved = this.solveShortAnswerRec(question.correctMappings, remainingSpots, availableSolutions!, sampleMappings);

        if (solved) {
            return sampleMappings;
        } else {
            return [];
        }
    }

    /**
     * Try to solve a short answer question recursively
     *
     * @param correctMappings {Array} the correct mappings defined by the creator of the question
     * @param remainingSpots {Array} the spots that still need to be mapped (recursion stops if this is empty)
     * @param availableSolutions {Array} the unused solutions that can still be used to map to spots (recursion stops if this is empty)
     * @param sampleMappings {Array} the mappings so far
     * @return {boolean} true, if the question was solved (solution is saved in sampleMappings), otherwise false
     */
    solveShortAnswerRec(
        correctMappings: ShortAnswerMapping[],
        remainingSpots: ShortAnswerSpot[],
        availableSolutions: ShortAnswerSolution[],
        sampleMappings: ShortAnswerMapping[],
    ): boolean {
        if (remainingSpots.length === 0) {
            return true;
        }

        const spot = remainingSpots[0];
        return availableSolutions.some((solution, index) => {
            const correctMapping = this.getShortAnswerMapping(correctMappings, solution, spot);
            if (correctMapping) {
                sampleMappings.push(correctMapping); // add new mapping
                remainingSpots.splice(0, 1); // remove first spot
                availableSolutions.splice(index, 1); // remove the used solution
                const solved = this.solveShortAnswerRec(correctMappings, remainingSpots, availableSolutions, sampleMappings);
                remainingSpots.splice(0, 0, spot); // re-insert first spot
                availableSolutions.splice(index, 0, solution); // re-insert the used solution
                if (!solved) {
                    sampleMappings.pop(); // remove new mapping (only if solution was not found)
                }
                return solved;
            } else {
                return false;
            }
        }, this);
    }

    /**
     * Validate that all correct mappings (and any combination of them that doesn't use a spot or solution twice)
     * can be used in a 100% correct solution.
     * This means that if any pair of solutions share a possible spot, then they must share all spots,
     * or in other words the sets of possible spots for these two solutions must be identical
     *
     * @param question {object} the question to check
     * @return {boolean} true, if the condition is met, otherwise false
     */

    validateNoMisleadingCorrectShortAnswerMapping(question: ShortAnswerQuestion) {
        if (!question.correctMappings) {
            // no correct mappings at all means there can be no misleading mappings
            return true;
        }
        let amountOfSolutionsThatShareOneSpot = 0;
        // iterate through all pairs of solutions
        if (question.solutions) {
            for (let i = 0; i < question.solutions.length; i++) {
                for (let j = 0; j < i; j++) {
                    // if these two solutions have one common spot, they must share all spots
                    const solution1 = question.solutions[i];
                    const solution2 = question.solutions[j];
                    const shareOneSpot = question.spots?.some((spot) => {
                        const isMappedWithSolution1 = this.isMappedTogether(question.correctMappings, solution1, spot);
                        const isMappedWithSolution2 = this.isMappedTogether(question.correctMappings, solution2, spot);
                        return isMappedWithSolution1 && isMappedWithSolution2;
                    }, this);
                    if (shareOneSpot) {
                        amountOfSolutionsThatShareOneSpot++;
                        const allSpotsForSolution1 = this.getAllSpotsForSolutions(question.correctMappings, solution1);
                        const allSpotsForSolution2 = this.getAllSpotsForSolutions(question.correctMappings, solution2);
                        // there have to be a least as many solutions that share all spots as the amount of existing spots
                        if (!this.isSameSetOfSpots(allSpotsForSolution1, allSpotsForSolution2) === true && amountOfSolutionsThatShareOneSpot <= question.spots!.length) {
                            // condition is violated for this pair of solutions
                            return false;
                        }
                    }
                }
            }
        }
        // condition was met for all pairs of solutions
        return true;
    }

    /**
     * Check if the given solution and spot are mapped together in the given mappings
     *
     * @param mappings {Array} the existing mappings to consider
     * @param solution {object} the solution to search for
     * @param spot {object} the spot to search for
     * @return {boolean} true if they are mapped together, otherwise false
     */
    isMappedTogether(mappings?: ShortAnswerMapping[], solution?: ShortAnswerSolution, spot?: ShortAnswerSpot) {
        return !!this.getShortAnswerMapping(mappings, solution, spot);
    }

    /**
     * Get all spots that are mapped to the given solutions
     *
     * @param mappings {Array} the existing mappings to consider
     * @param solution {object} the solution that the returned spots have to be mapped to
     * @return {Array} the resulting spots
     */
    getAllSpotsForSolutions(mappings?: ShortAnswerMapping[], solution?: ShortAnswerSolution) {
        return mappings
            ?.filter(function (mapping) {
                return this.isSameSolution(mapping.solution, solution);
            }, this)
            .map(function (mapping) {
                return mapping.spot!;
            });
    }

    /**
     * Get all solutions that are mapped to the given spot
     *
     * @param mappings {Array} the existing mappings to consider
     * @param spot {object} the spot for which to get the solutions
     * @return {Array} the resulting solutions
     */
    getAllSolutionsForSpot(mappings?: ShortAnswerMapping[], spot?: ShortAnswerSpot) {
        return mappings
            ?.filter((mapping) => {
                return this.isSameSpot(mapping.spot, spot);
            }, this)
            .map((mapping) => {
                return mapping.solution!;
            });
    }

    /**
     * Check if set1 and set2 contain the same solutions or spots
     *
     * @param set1 {Array} one set of solutions or spots
     * @param set2 {Array} another set of solutions or spots
     * @return {boolean} true if the sets contain the same items, otherwise false
     */
    isSameSetOfSpots(set1?: ShortAnswerSpot[], set2?: ShortAnswerSpot[]) {
        const service = this;
        if (set1?.length !== set2?.length) {
            // different number of elements => impossible to contain the same elements
            return false;
        }
        return (
            // for every element in set1 there has to be an identical element in set2 and vice versa
            set1?.every((spot1) => {
                return set2?.some((spot2) => {
                    return service.isSameSpot(spot1, spot2);
                });
            }) &&
            set2?.every((spot2) => {
                return set1?.some((spot1) => {
                    return service.isSameSpot(spot1, spot2);
                });
            })
        );
    }

    /**
     * Get the mapping that maps the given solution and spot together
     *
     * @param mappings {Array} the existing mappings to consider
     * @param solution {object} the solution to search for
     * @param spot {object} the spot to search for
     * @return the found mapping, or undefined if it doesn't exist
     */
    getShortAnswerMapping(mappings?: ShortAnswerMapping[], solution?: ShortAnswerSolution, spot?: ShortAnswerSpot) {
        const that = this;
        return mappings?.find((mapping: ShortAnswerMapping) => {
            return that.isSameSpot(spot, mapping.spot) && that.isSameSolution(solution, mapping.solution);
        }, this);
    }

    /**
     * compare if the two objects are the same spot
     *
     * @param a {object} a spot
     * @param b {object} another spot
     * @return {boolean}
     */
    isSameSpot(a?: ShortAnswerSpot, b?: ShortAnswerSpot): boolean {
        return a === b || (a !== undefined && b !== undefined && ((a.id && b.id && a.id === b.id) || (a.tempID != undefined && b.tempID != undefined && a.tempID === b.tempID)));
    }

    /**
     * compare if the two objects are the same solution
     *
     * @param a {object} a solution
     * @param b {object} another solution
     * @return {boolean}
     */
    isSameSolution(a?: ShortAnswerSolution, b?: ShortAnswerSolution): boolean {
        return a === b || (a !== undefined && b !== undefined && ((a.id && b.id && a.id === b.id) || (a.tempID != undefined && b.tempID != undefined && a.tempID === b.tempID)));
    }

    /**
     * checks if every spot has a solution
     *
     * @param mappings {object} mappings
     * @param spots {object} spots
     * @return {boolean}
     */
    everySpotHasASolution(mappings: ShortAnswerMapping[], spots: ShortAnswerSpot[]): boolean {
        let i = 0;
        for (const spot of spots) {
            const solutions = this.getAllSolutionsForSpot(mappings, spot);
            if (solutions && solutions.length > 0) {
                i++;
            }
        }
        return i === spots.length;
    }

    /**
     * checks if every mapped solution has a spot
     *
     * @param mappings {object} mappings
     * @return {boolean}
     */
    everyMappedSolutionHasASpot(mappings: ShortAnswerMapping[]): boolean {
        return !(mappings.filter((mapping) => mapping.spot === undefined).length > 0);
    }

    /**
     * checks if mappings have duplicate values
     *
     * @param mappings
     * @return {boolean}
     */
    hasMappingDuplicateValues(mappings: ShortAnswerMapping[]): boolean {
        if (mappings.filter((mapping) => mapping.spot === undefined).length > 0) {
            return false;
        }
        let duplicateValues = 0;
        for (let i = 0; i < mappings.length - 1; i++) {
            for (let j = i + 1; j < mappings.length; j++) {
                if (mappings[i].spot!.spotNr === mappings[j].spot!.spotNr && mappings[i].solution!.text!.toLowerCase() === mappings[j].solution!.text!.toLowerCase()) {
                    duplicateValues++;
                }
            }
        }
        return duplicateValues > 0;
    }

    /**
     * Display a sample solution instead of the student's answer
     *
     * @param {ShortAnswerQuestion} question
     * @returns {ShortAnswerSolution[]}
     */
    getSampleSolutions(question: ShortAnswerQuestion): ShortAnswerSolution[] {
        const sampleSolutions: ShortAnswerSolution[] = [];
        for (const spot of question.spots!) {
            const solutionsForSpot = this.getAllSolutionsForSpot(question.correctMappings!, spot);
            for (const mapping of question.correctMappings!) {
                if (
                    mapping.spot!.id === spot.id &&
                    !sampleSolutions.some(
                        (sampleSolution) => sampleSolution.text === mapping.solution!.text && !this.allSolutionsAreInSampleSolution(solutionsForSpot, sampleSolutions),
                    )
                ) {
                    sampleSolutions.push(mapping.solution!);
                    break;
                }
            }
        }
        return sampleSolutions;
    }

    /**
     * checks if all solutions are in the sample solution
     *
     * @param {ShortAnswerSolution[]} solutionsForSpot
     * @param {ShortAnswerSolution[]} sampleSolutions
     * @returns {boolean}
     */
    allSolutionsAreInSampleSolution(solutionsForSpot?: ShortAnswerSolution[], sampleSolutions?: ShortAnswerSolution[]): boolean {
        let i = 0;
        for (const solutionForSpot of solutionsForSpot || []) {
            for (const sampleSolution of sampleSolutions || []) {
                if (solutionForSpot.text === sampleSolution.text) {
                    i++;
                    break;
                }
            }
        }
        return i === solutionsForSpot?.length;
    }

    /**
     * checks if at least there are as many solutions as spots
     *
     * @param {ShortAnswerQuestion} question
     * @returns {boolean}
     */
    atLeastAsManySolutionsAsSpots(question: ShortAnswerQuestion): boolean {
        return question.spots!.length <= question.solutions!.length;
    }

    /**
     * We create now the structure on how to display the text of the question
     * 1. The question text is split at every new line. The first element of the array would be then the first line of the question text.
     * 2. Now each line of the question text will be divided into text before spot tag, spot tag and text after spot tag.
     * (e.g 'Enter [-spot 1] long [-spot 2] if needed' will be transformed to [["Enter", "[-spot 1]", "long", "[-spot 2]", "if needed"]])
     *
     * @param questionText
     * @returns {string[][]}
     */
    divideQuestionTextIntoTextParts(questionText: string): string[][] {
        const spotRegExpo = /\[-spot\s*[0-9]+\]/g;

        /**
         * Interleaves elements of two lists xs and ys recursively
         * @param x First element
         * @param xs Rest of the list
         * @param ys Other list
         */
        function interleave([x, ...xs]: string[], ys: string[] = []): string[] {
            return x === undefined
                ? ys // base: no x
                : [x, ...interleave(ys, xs)]; // inductive: some x
        }

        return questionText.split(/\n/g).map((line) => {
            const spots = line.match(spotRegExpo) || [];
            const texts = line.split(spotRegExpo);
            return interleave(texts, spots).filter((x) => x.length > 0);
        });
    }

    /**
     * checks if text is an input field (check for spot tag)
     * @param text
     */
    isInputField(text: string): boolean {
        return !(text.search(/\[-spot/g) === -1);
    }

    /**
     * gets just the spot number
     * @param text
     */
    getSpotNr(text: string): number {
        // separate "[-spot 1]" into just "1"
        return +text
            .split(/\[-spot/g)[1]
            .split(']')[0]
            .trim();
    }

    /**
     * gets the spot for a specific spotNr
     * @param spotNr the spot number for which the sport should be retrived
     * @param question
     */
    getSpot(spotNr: number, question: ShortAnswerQuestion): ShortAnswerSpot {
        return question.spots!.filter((spot) => spot.spotNr === spotNr)[0];
    }

    /**
     * We transform now the different text parts of the question text to HTML.
     * 1. We iterate through every line of the question text.
     * 2. We iterate through every element of each line of the question text and set each element with the new HTML.
     * @param textParts
     * @param artemisMarkdown
     * @returns {string[][]}
     */
    transformTextPartsIntoHTML(textParts: string[][], artemisMarkdown: ArtemisMarkdownService): string[][] {
        const formattedTextParts = textParts.map((textPart) => textPart.map((element) => artemisMarkdown.htmlForMarkdown(element.trim())));
        return this.addIndentationToTextParts(textParts, formattedTextParts);
    }

    /**
     * @function addIndentationToTextParts
     * @desc Formats the first word of each line with the indentation it originally had.
     * @param originalTextParts {string[][]} the text parts without html formatting
     * @param formattedTextParts {string[][]} the text parts with html formatting
     */
    addIndentationToTextParts(originalTextParts: string[][], formattedTextParts: string[][]): string[][] {
        for (let i = 0; i < formattedTextParts.length; i++) {
            const element = formattedTextParts[i][0];
            const firstWord = this.getFirstWord(originalTextParts[i][0]);
            if (firstWord === '') {
                continue;
            }
            const firstWordIndex = element.indexOf(firstWord);
            const whitespace = '&nbsp;'.repeat(this.getIndentation(originalTextParts[i][0]).length);
            formattedTextParts[i][0] = [element.substring(0, firstWordIndex), whitespace, element.substring(firstWordIndex).trim()].join('');
        }
        return formattedTextParts;
    }

    /**
     * @function getIndentation
     * @desc Returns the whitespace in front of the text.
     * @param text {string} the text for which we get the indentation
     */
    getIndentation(text: string): string {
        if (text.startsWith('`')) {
            text = text.substring(1);
        }
        let index = 0;
        let indentation = '';
        while (text[index] === ' ') {
            indentation = indentation.concat(' ');
            index++;
        }
        return indentation;
    }

    /**
     * @function getFirstWord
     * @desc Returns the first word in a text.
     * @param text {string} for which the first word is returned
     */
    getFirstWord(text: string): string {
        const words = text.split(' ').filter((word) => word !== '');
        if (words.length === 0) {
            return '';
        } else if (words[0] === '`') {
            return words[1];
        } else {
            return words[0].startsWith('`') ? words[0].substring(1) : words[0];
        }
    }
}
