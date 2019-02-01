import { Injectable } from '@angular/core';
import { ShortAnswerSolution } from '../../entities/short-answer-solution';
import { ShortAnswerSpot } from '../../entities/short-answer-spot';
import { ShortAnswerMapping } from '../../entities/short-answer-mapping';
import { ShortAnswerQuestion} from '../../entities/short-answer-question';

@Injectable({ providedIn: 'root' })
export class ShortAnswerQuestionUtil {
    constructor() {}

    /**
     * Get a sample solution for the given drag and drop question
     *
     * @param question {object} the drag and drop question we want to solve
     * @param [mappings] {Array} (optional) the mappings we try to use in the sample solution (this may contain incorrect mappings - they will be filtered out)
     * @return {Array} array of mappings that would solve this question (may be empty, if question is unsolvable)
     */
    solveSA(question: ShortAnswerQuestion, mappings: ShortAnswerMapping[]) {
        if (!question.correctMappings) {
            return [];
        }

        const sampleMappings = new Array<ShortAnswerMapping>();
        let availableSolutions = question.solutions;

        // filter out dropLocations that do not need to be mapped
        let remainingSpots: ShortAnswerSpot [] = question.spots.filter(function(spot) {
            return question.correctMappings.some(function(mapping) {
                return this.isSameSpot(mapping.spot, spot);
            }, this);
        }, this);

        if (remainingSpots.length !== question.spots.length) {
            return [];
        }

        if (mappings) {
            // add mappings that are already correct
            mappings.forEach(function(mapping) {
                const correctMapping = this.getSAMapping(question.correctMappings, mapping.solution, mapping.spot);
                if (correctMapping) {
                    sampleMappings.push(correctMapping);
                    remainingSpots = remainingSpots.filter(function(spot) {
                        return !this.isSameSpot(spot, mapping.spot);
                    }, this);
                    availableSolutions = availableSolutions.filter(function(solution) {
                        return !this.isSameSolution(solution, mapping.solution);
                    }, this);
                }
            }, this);
        }

        // solve recursively
        const solved = this.solveSARec(question.correctMappings, remainingSpots, availableSolutions, sampleMappings);

        if (solved) {
            return sampleMappings;
        } else {
            return [];
        }
    }

    /**
     * Try to solve a drag and drop question recursively
     *
     * @param correctMappings {Array} the correct mappings defined by the creator of the question
     * @param remainingDropLocations {Array} the drop locations that still need to be mapped (recursion stops if this is empty)
     * @param availableDragItems {Array} the unused drag items that can still be used to map to drop locations (recursion stops if this is empty)
     * @param sampleMappings {Array} the mappings so far
     * @return {boolean} true, if the question was solved (solution is saved in sampleMappings), otherwise false
     */
    solveSARec(
        correctMappings: ShortAnswerMapping[],
        remainingSpots: ShortAnswerSpot[],
        availableSolutions: ShortAnswerSolution[],
        sampleMappings: ShortAnswerMapping[]
    ) {
        if (remainingSpots.length === 0) {
            return true;
        }

        const spot = remainingSpots[0];
        return availableSolutions.some(function(solution, index) {
            const correctMapping = this.getSAMapping(correctMappings, solution, spot);
            if (correctMapping) {
                sampleMappings.push(correctMapping); // add new mapping
                remainingSpots.splice(0, 1); // remove first dropLocation
                availableSolutions.splice(index, 1); // remove the used dragItem
                const solved = this.solveSARec(correctMappings, remainingSpots, availableSolutions, sampleMappings);
                remainingSpots.splice(0, 0, spot); // re-insert first dropLocation
                availableSolutions.splice(index, 0, solution); // re-insert the used dragItem
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
     * Validate that all correct mappings (and any combination of them that doesn't use a dropLocation or dragItem twice)
     * can be used in a 100% correct solution.
     * This means that if any pair of dragItems share a possible dropLocation, then they must share all dropLocations,
     * or in other words the sets of possible dropLocations for these two dragItems must be identical
     *
     * @param question {object} the question to check
     * @return {boolean} true, if the condition is met, otherwise false
     */

    validateNoMisleadingCorrectSAMapping(question: ShortAnswerQuestion) {
        if (!question.correctMappings) {
            // no correct mappings at all means there can be no misleading mappings
            return true;
        }
        // iterate through all pairs of drag items
        for (let i = 0; i < question.solutions.length; i++) {
            for (let j = 0; j < i; j++) {
                // if these two drag items have one common drop location, they must share all drop locations
                const solution1 = question.solutions[i];
                const solution2 = question.solutions[j];
                const shareOneSpot = question.spots.some(function(spot) {
                    const isMappedWithSolution1 = this.isSAMappedTogether(question.correctMappings, solution1, spot);
                    const isMappedWithSolution2 = this.isSAMappedTogether(question.correctMappings, solution2, spot);
                    return isMappedWithSolution1 && isMappedWithSolution2;
                }, this);
                if (shareOneSpot) {
                    const allSpotsForSolution1 = this.getAllSpotsForSolutions(question.correctMappings, solution1);
                    const allSpotsForSolution2 = this.getAllSpotsForSolutions(question.correctMappings, solution2);
                    if (!this.isSameSetOfSpots(allSpotsForSolution1, allSpotsForSolution2)) {
                        // condition is violated for this pair of dragItems
                        return false;
                    }
                }
            }
        }
        // condition was met for all pairs of drag items
        return true;
    }

    /**
     * Check if the given dragItem and dropLocation are mapped together in the given mappings
     *
     * @param mappings {Array} the existing mappings to consider
     * @param dragItem {object} the drag item to search for
     * @param dropLocation {object} the drop location to search for
     * @return {boolean} true if they are mapped together, otherwise false
     */
    isSAMappedTogether(mappings: ShortAnswerMapping[], solution: ShortAnswerSolution, spot: ShortAnswerSpot) {
        return !!this.getSAMapping(mappings, solution, spot);
    }

    /**
     * Get all drop locations that are mapped to the given drag items
     *
     * @param mappings {Array} the existing mappings to consider
     * @param dragItem {object} the drag item that the returned drop locations have to be mapped to
     * @return {Array} the resulting drop locations
     */
    getAllSpotsForSolutions(mappings: ShortAnswerMapping[], solution: ShortAnswerSolution): ShortAnswerSpot[] {
        return mappings
            .filter(function(mapping) {
                return this.isSameSolution(mapping.solution, solution);
            }, this)
            .map(function(mapping) {
                return mapping.spot;
            });
    }

    /**
     * Get all solutions that are mapped to the given spot
     *
     * @param mappings {Array} the existing mappings to consider
     * @param dragItem {object} the drag item that the returned drop locations have to be mapped to
     * @return {Array} the resulting drop locations
     */
    getAllSolutionsForSpot(mappings: ShortAnswerMapping[], spot: ShortAnswerSpot): ShortAnswerSolution[] {
        return mappings
            .filter(function(mapping) {
                return this.isSameSpot(mapping.spot, spot);
            }, this)
            .map(function(mapping) {
                return mapping.solution;
            });
    }

    /**
     * Check if set1 and set2 contain the same drag items or drop locations
     *
     * @param set1 {Array} one set of drag items or drop locations
     * @param set2 {Array} another set of drag items or drop locations
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
            set1.every(function(element1: ShortAnswerSpot) {
                return set2.some(function(element2: ShortAnswerSpot) {
                    return service.isSameSpot(element1, element2);
                });
            }) &&
            set2.every(function(element2: ShortAnswerSpot) {
                return set1.some(function(element1: ShortAnswerSpot) {
                    return service.isSameSpot(element1, element2);
                });
            })
        );
    }

    /**
     * Get the mapping that maps the given dragItem and dropLocation together
     *
     * @param mappings {Array} the existing mappings to consider
     * @param dragItem {object} the drag item to search for
     * @param dropLocation {object} the drop location to search for
     * @return {object | null} the found mapping, or null if it doesn't exist
     */
    getSAMapping(mappings: ShortAnswerMapping[], solution: ShortAnswerSolution, spot: ShortAnswerSpot) {
        const that = this;
        return mappings.find(function(mapping: ShortAnswerMapping) {
            return that.isSameSpot(spot, mapping.spot) && that.isSameSolution(solution, mapping.solution);
        }, this);
    }

    /**
     * compare if the two objects are drop location
     *
     * @param a {object} a drop location
     * @param b {object} another drop location
     * @return {boolean}
     */
    isSameSpot(a: ShortAnswerSpot, b: ShortAnswerSpot): boolean {
        return a === b || (a && b && ((a.id && b.id && a.id === b.id) || (a.tempID && b.tempID && a.tempID === b.tempID)));
    }

    /**
     * compare if the two objects are the same drag item
     *
     * @param a {object} a drag item
     * @param b {object} another drag item
     * @return {boolean}
     */
    isSameSolution(a: ShortAnswerSolution, b: ShortAnswerSolution): boolean {
        return a === b || (a && b && ((a.id && b.id && a.id === b.id) || (a.tempID && b.tempID && a.tempID === b.tempID)));
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
        return !(mappings.filter(mapping => mapping.spot === undefined).length > 0);
    }

    /**
     * checks if the first line of the question text is the question
     *
     * @param text
     * @return {string}
     */
    firstLineOfQuestion(text: string): string {
        // first line is the question if there is no [-spot #] tag in the string
        if (text.split(/\n/g)[0].search(/\[-spot/g) === -1) {
        return text.split(/\n/g)[0];
        } else {
            return '';
        }
    }

    /**
     * separates first line from the rest of the text
     *
     * @param text
     * @return {string}
     */
    separateFirstLineOfQuestionFromRestOfText(text: string): string {
        let firstLineHasQuestion = false;

        if (this.firstLineOfQuestion(text) !== '') {
            firstLineHasQuestion = true;
        }

        let questionTextSplitAtNewLine = '';

        // separates the the rest of the text from the question
        if (firstLineHasQuestion) {
            questionTextSplitAtNewLine = text
                .split(/\n+/g)
                .slice(1)
                .join();
        } else {
            questionTextSplitAtNewLine = text
                .split(/\n+/g)
                .join();
        }

        // checks if a line break is in the text (marked by "," and replaces it) and check if text is a list
        if (questionTextSplitAtNewLine.includes(',')) {
            questionTextSplitAtNewLine = questionTextSplitAtNewLine.replace(/\,/g, ' ');
        }
        return questionTextSplitAtNewLine;
    }

    /**
     * checks if question format is a list or text
     *
     * @param text
     * @return {boolean}
     */
    isQuestionAList(text: string): boolean {
        if (this.separateFirstLineOfQuestionFromRestOfText(text)
            .includes('1.')) {
            return true;
        }
    }

    /**
     * splits the text at the "[-spot " tag to have the parts of the text without the spots
     *
     * @param text
     * @return {boolean}
     */
    getTextWithoutSpots(text: string): string[] {
        return this.separateFirstLineOfQuestionFromRestOfText(text)
            .split(/\[-spot\s\d\]/g);
    }
}
