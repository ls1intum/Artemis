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
        for (let i = 0; i < question.solutions.length; i++) {
            for (let j = 0; j < i; j++) {
                // if these two solutions have one common spot, they must share all spots
                const solution1 = question.solutions[i];
                const solution2 = question.solutions[j];
                const shareOneSpot = question.spots.some(function (spot) {
                    const isMappedWithSolution1 = this.isMappedTogether(question.correctMappings, solution1, spot);
                    const isMappedWithSolution2 = this.isMappedTogether(question.correctMappings, solution2, spot);
                    return isMappedWithSolution1 && isMappedWithSolution2;
                }, this);
                if (shareOneSpot) {
                    amountOfSolutionsThatShareOneSpot++;
                    const allSpotsForSolution1 = this.getAllSpotsForSolution(question.correctMappings, solution1);
                    const allSpotsForSolution2 = this.getAllSpotsForSolution(question.correctMappings, solution2);
                    // there have to be a least as many solutions that share all spots as the amount of existing spots
                    if (!this.isSameSetOfSpots(allSpotsForSolution1, allSpotsForSolution2) && amountOfSolutionsThatShareOneSpot <= question.spots.length) {
                        // condition is violated for this pair of solutions
                        return false;
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
    isMappedTogether(mappings: ShortAnswerMapping[], solution: ShortAnswerSolution, spot: ShortAnswerSpot) {
        return !!this.getShortAnswerMapping(mappings, solution, spot);
    }

    /**
     * Get all spots that are mapped to the given solutions
     *
     * @param mappings {Array} the existing mappings to consider
     * @param solution {object} the solution that the returned spots have to be mapped to
     * @return {Array} the resulting spots
     */
    getAllSpotsForSolution(mappings: ShortAnswerMapping[], solution: ShortAnswerSolution): ShortAnswerSpot[] {
        return mappings
            .filter(function (mapping) {
                return this.isSameSolution(mapping.solution, solution);
            }, this)
            .map(function (mapping) {
                return mapping.spot;
            });
    }

    /**
     * Get all solutions that are mapped to the given spot
     *
     * @param mappings {Array} the existing mappings to consider
     * @param spot {object} the spot for which to get the solutions
     * @return {Array} the resulting solutions
     */
    getAllSolutionsForSpot(mappings: ShortAnswerMapping[], spot: ShortAnswerSpot): ShortAnswerSolution[] {
        return mappings
            .filter(function (mapping) {
                return this.isSameSpot(mapping.spot, spot);
            }, this)
            .map(function (mapping) {
                return mapping.solution;
            });
    }

    /**
     * Check if set1 and set2 contain the same solutions or spots
     *
     * @param set1 {Array} one set of solutions or spots
     * @param set2 {Array} another set of solutions or spots
     * @return {boolean} true if the sets contain the same items, otherwise false
     */
    isSameSetOfSpots(set1: ShortAnswerSpot[], set2: ShortAnswerSpot[]): boolean {
        const service = this;
        if (set1.length !== set2.length) {
            // different number of elements => impossible to contain the same elements
            return false;
        }
        return (
            // for every element in set1 there has to be an identical element in set2 and vice versa
            set1.every(function (element1: ShortAnswerSpot) {
                return set2.some(function (element2: ShortAnswerSpot) {
                    return service.isSameSpot(element1, element2);
                });
            }) &&
            set2.every(function (element2: ShortAnswerSpot) {
                return set1.some(function (element1: ShortAnswerSpot) {
                    return service.isSameSpot(element1, element2);
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
     * @return {object | null} the found mapping, or null if it doesn't exist
     */
    getShortAnswerMapping(mappings: ShortAnswerMapping[], solution: ShortAnswerSolution, spot: ShortAnswerSpot) {
        const that = this;
        return mappings.find(function (mapping: ShortAnswerMapping) {
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
    isSameSpot(a: ShortAnswerSpot, b: ShortAnswerSpot): boolean {
        return a === b || (a && b && ((a.id && b.id && a.id === b.id) || (a.tempID != null && b.tempID != null && a.tempID === b.tempID)));
    }

    /**
     * compare if the two objects are the same solution
     *
     * @param a {object} a solution
     * @param b {object} another solution
     * @return {boolean}
     */
    isSameSolution(a: ShortAnswerSolution, b: ShortAnswerSolution): boolean {
        return a === b || (a && b && ((a.id && b.id && a.id === b.id) || (a.tempID != null && b.tempID != null && a.tempID === b.tempID)));
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
            if (this.getAllSolutionsForSpot(mappings, spot).length > 0) {
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
                if (mappings[i].spot.spotNr === mappings[j].spot.spotNr && mappings[i].solution.text.toLowerCase() === mappings[j].solution.text.toLowerCase()) {
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
        for (const spot of question.spots) {
            const solutionsForSpot = this.getAllSolutionsForSpot(question.correctMappings, spot);
            for (const mapping of question.correctMappings) {
                if (
                    mapping.spot.id === spot.id &&
                    !sampleSolutions.some(
                        (sampleSolution) => sampleSolution.text === mapping.solution.text && !this.allSolutionsAreInSampleSolution(solutionsForSpot, sampleSolutions),
                    )
                ) {
                    sampleSolutions.push(mapping.solution);
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
    allSolutionsAreInSampleSolution(solutionsForSpot: ShortAnswerSolution[], sampleSolutions: ShortAnswerSolution[]): boolean {
        let i = 0;
        for (const solutionForSpot of solutionsForSpot) {
            for (const sampleSolution of sampleSolutions) {
                if (solutionForSpot.text === sampleSolution.text) {
                    i++;
                    break;
                }
            }
        }
        return i === solutionsForSpot.length;
    }

    /**
     * checks if at least there are as many solutions as spots
     *
     * @param {ShortAnswerQuestion} question
     * @returns {boolean}
     */
    atLeastAsManySolutionsAsSpots(question: ShortAnswerQuestion): boolean {
        return question.spots.length <= question.solutions.length;
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
            return interleave(texts, spots)
                .map((x) => x.trim())
                .filter((x) => x.length > 0);
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
        return question.spots.filter((spot) => spot.spotNr === spotNr)[0];
    }

    /**
     * We transform now the different text parts of the question text to HTML.
     * 1. We iterate through every line of the question text.
     * 2. We iterate through every element of each line of the question text and set each element with the new HTML.
     * @param textParts
     * @param artemisMarkdown
     * @returns {string[][]}
     */
    transformTextPartsIntoHTML(textParts: string[][], artemisMarkdown: ArtemisMarkdownService): (string | null)[][] {
        return textParts.map((textPart) => textPart.map((element) => artemisMarkdown.htmlForMarkdown(element)));
    }
}
