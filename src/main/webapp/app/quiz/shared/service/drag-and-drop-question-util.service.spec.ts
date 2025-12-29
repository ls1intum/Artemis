import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { DragAndDropQuestionUtil } from './drag-and-drop-question-util.service';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';

describe('DragAndDropQuestionUtil', () => {
    setupTestBed({ zoneless: true });

    let service: DragAndDropQuestionUtil;

    beforeEach(() => {
        service = TestBed.inject(DragAndDropQuestionUtil);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('solve', () => {
        it('should return empty array when no correct mappings', () => {
            const question: DragAndDropQuestion = {
                correctMappings: undefined,
            } as DragAndDropQuestion;

            const result = service.solve(question);

            expect(result).toEqual([]);
        });

        it('should return empty array when correct mappings is empty', () => {
            const question = {
                correctMappings: [],
                dragItems: [{ id: 1, invalid: false }],
                dropLocations: [{ id: 1, invalid: false }],
                randomizeOrder: true,
                invalid: false,
                exportQuiz: false,
            } as DragAndDropQuestion;

            const result = service.solve(question);

            expect(result).toEqual([]);
        });

        it('should solve simple question with one mapping', () => {
            const dragItem: DragItem = { id: 1, invalid: false };
            const dropLocation: DropLocation = { id: 1, invalid: false };
            const correctMapping: DragAndDropMapping = { dragItem, dropLocation, invalid: false };

            const question: DragAndDropQuestion = {
                correctMappings: [correctMapping],
                dragItems: [dragItem],
                dropLocations: [dropLocation],
            } as DragAndDropQuestion;

            const result = service.solve(question);

            expect(result.length).toBe(1);
            expect(result[0]).toBe(correctMapping);
        });

        it('should solve question with multiple mappings', () => {
            const dragItem1: DragItem = { id: 1, invalid: false };
            const dragItem2: DragItem = { id: 2, invalid: false };
            const dropLocation1: DropLocation = { id: 1, invalid: false };
            const dropLocation2: DropLocation = { id: 2, invalid: false };

            const correctMapping1: DragAndDropMapping = { dragItem: dragItem1, dropLocation: dropLocation1, invalid: false };
            const correctMapping2: DragAndDropMapping = { dragItem: dragItem2, dropLocation: dropLocation2, invalid: false };

            const question: DragAndDropQuestion = {
                correctMappings: [correctMapping1, correctMapping2],
                dragItems: [dragItem1, dragItem2],
                dropLocations: [dropLocation1, dropLocation2],
            } as DragAndDropQuestion;

            const result = service.solve(question);

            expect(result.length).toBe(2);
        });

        it('should use existing correct mappings from input', () => {
            const dragItem1: DragItem = { id: 1, invalid: false };
            const dragItem2: DragItem = { id: 2, invalid: false };
            const dropLocation1: DropLocation = { id: 1, invalid: false };
            const dropLocation2: DropLocation = { id: 2, invalid: false };

            const correctMapping1: DragAndDropMapping = { dragItem: dragItem1, dropLocation: dropLocation1, invalid: false };
            const correctMapping2: DragAndDropMapping = { dragItem: dragItem2, dropLocation: dropLocation2, invalid: false };

            const question: DragAndDropQuestion = {
                correctMappings: [correctMapping1, correctMapping2],
                dragItems: [dragItem1, dragItem2],
                dropLocations: [dropLocation1, dropLocation2],
            } as DragAndDropQuestion;

            // Provide partial solution
            const existingMappings: DragAndDropMapping[] = [correctMapping1];

            const result = service.solve(question, existingMappings);

            expect(result.length).toBe(2);
        });

        it('should filter out incorrect mappings from input', () => {
            const dragItem1: DragItem = { id: 1, invalid: false };
            const dragItem2: DragItem = { id: 2, invalid: false };
            const dropLocation1: DropLocation = { id: 1, invalid: false };
            const dropLocation2: DropLocation = { id: 2, invalid: false };

            const correctMapping1: DragAndDropMapping = { dragItem: dragItem1, dropLocation: dropLocation1, invalid: false };
            const correctMapping2: DragAndDropMapping = { dragItem: dragItem2, dropLocation: dropLocation2, invalid: false };

            const question: DragAndDropQuestion = {
                correctMappings: [correctMapping1, correctMapping2],
                dragItems: [dragItem1, dragItem2],
                dropLocations: [dropLocation1, dropLocation2],
            } as DragAndDropQuestion;

            // Provide incorrect mapping
            const incorrectMappings: DragAndDropMapping[] = [{ dragItem: dragItem1, dropLocation: dropLocation2, invalid: false }];

            const result = service.solve(question, incorrectMappings);

            expect(result.length).toBe(2);
        });
    });

    describe('validateNoMisleadingCorrectMapping', () => {
        it('should return true when no correct mappings', () => {
            const question: DragAndDropQuestion = {
                correctMappings: undefined,
                dragItems: [{ id: 1, invalid: false }],
            } as DragAndDropQuestion;

            const result = service.validateNoMisleadingCorrectMapping(question);

            expect(result).toBe(true);
        });

        it('should return true when no drag items', () => {
            const question = {
                correctMappings: [],
                dragItems: undefined,
                randomizeOrder: true,
                invalid: false,
                exportQuiz: false,
            } as DragAndDropQuestion;

            const result = service.validateNoMisleadingCorrectMapping(question);

            expect(result).toBe(true);
        });

        it('should return true for valid non-misleading mappings', () => {
            const dragItem1: DragItem = { id: 1, invalid: false };
            const dragItem2: DragItem = { id: 2, invalid: false };
            const dropLocation1: DropLocation = { id: 1, invalid: false };
            const dropLocation2: DropLocation = { id: 2, invalid: false };

            const question: DragAndDropQuestion = {
                correctMappings: [
                    { dragItem: dragItem1, dropLocation: dropLocation1, invalid: false },
                    { dragItem: dragItem2, dropLocation: dropLocation2, invalid: false },
                ],
                dragItems: [dragItem1, dragItem2],
                dropLocations: [dropLocation1, dropLocation2],
            } as DragAndDropQuestion;

            const result = service.validateNoMisleadingCorrectMapping(question);

            expect(result).toBe(true);
        });

        it('should return false for misleading mappings', () => {
            const dragItem1: DragItem = { id: 1, invalid: false };
            const dragItem2: DragItem = { id: 2, invalid: false };
            const dropLocation1: DropLocation = { id: 1, invalid: false };
            const dropLocation2: DropLocation = { id: 2, invalid: false };

            // Both drag items can go to dropLocation1, but only dragItem1 can go to dropLocation2
            // This is misleading because they share one drop location but not all
            const question: DragAndDropQuestion = {
                correctMappings: [
                    { dragItem: dragItem1, dropLocation: dropLocation1, invalid: false },
                    { dragItem: dragItem2, dropLocation: dropLocation1, invalid: false },
                    { dragItem: dragItem1, dropLocation: dropLocation2, invalid: false },
                ],
                dragItems: [dragItem1, dragItem2],
                dropLocations: [dropLocation1, dropLocation2],
            } as DragAndDropQuestion;

            const result = service.validateNoMisleadingCorrectMapping(question);

            expect(result).toBe(false);
        });

        it('should return true when drag items share all drop locations', () => {
            const dragItem1: DragItem = { id: 1, invalid: false };
            const dragItem2: DragItem = { id: 2, invalid: false };
            const dropLocation1: DropLocation = { id: 1, invalid: false };
            const dropLocation2: DropLocation = { id: 2, invalid: false };

            // Both drag items can go to both drop locations - not misleading
            const question: DragAndDropQuestion = {
                correctMappings: [
                    { dragItem: dragItem1, dropLocation: dropLocation1, invalid: false },
                    { dragItem: dragItem2, dropLocation: dropLocation1, invalid: false },
                    { dragItem: dragItem1, dropLocation: dropLocation2, invalid: false },
                    { dragItem: dragItem2, dropLocation: dropLocation2, invalid: false },
                ],
                dragItems: [dragItem1, dragItem2],
                dropLocations: [dropLocation1, dropLocation2],
            } as DragAndDropQuestion;

            const result = service.validateNoMisleadingCorrectMapping(question);

            expect(result).toBe(true);
        });
    });

    describe('isMappedTogether', () => {
        it('should return true when mapping exists', () => {
            const dragItem: DragItem = { id: 1, invalid: false };
            const dropLocation: DropLocation = { id: 1, invalid: false };
            const mappings: DragAndDropMapping[] = [{ dragItem, dropLocation, invalid: false }];

            const result = service.isMappedTogether(mappings, dragItem, dropLocation);

            expect(result).toBe(true);
        });

        it('should return false when mapping does not exist', () => {
            const dragItem: DragItem = { id: 1, invalid: false };
            const dropLocation: DropLocation = { id: 1, invalid: false };
            const otherDropLocation: DropLocation = { id: 2, invalid: false };
            const mappings: DragAndDropMapping[] = [{ dragItem, dropLocation, invalid: false }];

            const result = service.isMappedTogether(mappings, dragItem, otherDropLocation);

            expect(result).toBe(false);
        });
    });

    describe('getMapping', () => {
        it('should return mapping when found', () => {
            const dragItem: DragItem = { id: 1, invalid: false };
            const dropLocation: DropLocation = { id: 1, invalid: false };
            const mapping: DragAndDropMapping = { dragItem, dropLocation, invalid: false };
            const mappings: DragAndDropMapping[] = [mapping];

            const result = service.getMapping(mappings, dragItem, dropLocation);

            expect(result).toBe(mapping);
        });

        it('should return undefined when not found', () => {
            const dragItem: DragItem = { id: 1, invalid: false };
            const dropLocation: DropLocation = { id: 1, invalid: false };
            const mappings: DragAndDropMapping[] = [];

            const result = service.getMapping(mappings, dragItem, dropLocation);

            expect(result).toBeUndefined();
        });
    });

    describe('getAllDropLocationsForDragItem', () => {
        it('should return all drop locations for a drag item', () => {
            const dragItem: DragItem = { id: 1, invalid: false };
            const dropLocation1: DropLocation = { id: 1, invalid: false };
            const dropLocation2: DropLocation = { id: 2, invalid: false };
            const mappings: DragAndDropMapping[] = [
                { dragItem, dropLocation: dropLocation1, invalid: false },
                { dragItem, dropLocation: dropLocation2, invalid: false },
            ];

            const result = service.getAllDropLocationsForDragItem(mappings, dragItem);

            expect(result.length).toBe(2);
            expect(result).toContain(dropLocation1);
            expect(result).toContain(dropLocation2);
        });

        it('should return empty array when no mappings', () => {
            const dragItem: DragItem = { id: 1, invalid: false };
            const mappings: DragAndDropMapping[] = [];

            const result = service.getAllDropLocationsForDragItem(mappings, dragItem);

            expect(result).toEqual([]);
        });
    });

    describe('isSameSetOfDropLocations', () => {
        it('should return true for identical sets', () => {
            const dropLocation1: DropLocation = { id: 1, invalid: false };
            const dropLocation2: DropLocation = { id: 2, invalid: false };
            const set1 = [dropLocation1, dropLocation2];
            const set2 = [dropLocation1, dropLocation2];

            const result = service.isSameSetOfDropLocations(set1, set2);

            expect(result).toBe(true);
        });

        it('should return true for same elements in different order', () => {
            const dropLocation1: DropLocation = { id: 1, invalid: false };
            const dropLocation2: DropLocation = { id: 2, invalid: false };
            const set1 = [dropLocation1, dropLocation2];
            const set2 = [dropLocation2, dropLocation1];

            const result = service.isSameSetOfDropLocations(set1, set2);

            expect(result).toBe(true);
        });

        it('should return false for different lengths', () => {
            const dropLocation1: DropLocation = { id: 1, invalid: false };
            const dropLocation2: DropLocation = { id: 2, invalid: false };
            const set1 = [dropLocation1, dropLocation2];
            const set2 = [dropLocation1];

            const result = service.isSameSetOfDropLocations(set1, set2);

            expect(result).toBe(false);
        });

        it('should return false for different elements', () => {
            const dropLocation1: DropLocation = { id: 1, invalid: false };
            const dropLocation2: DropLocation = { id: 2, invalid: false };
            const dropLocation3: DropLocation = { id: 3, invalid: false };
            const set1 = [dropLocation1, dropLocation2];
            const set2 = [dropLocation1, dropLocation3];

            const result = service.isSameSetOfDropLocations(set1, set2);

            expect(result).toBe(false);
        });
    });

    describe('isSameEntityWithTempId', () => {
        it('should return true for same object reference', () => {
            const entity = { id: 1 };
            const result = service.isSameEntityWithTempId(entity, entity);
            expect(result).toBe(true);
        });

        it('should return true for same id', () => {
            const entity1 = { id: 1 };
            const entity2 = { id: 1 };
            const result = service.isSameEntityWithTempId(entity1, entity2);
            expect(result).toBe(true);
        });

        it('should return true for same tempID', () => {
            const entity1 = { tempID: 123 };
            const entity2 = { tempID: 123 };
            const result = service.isSameEntityWithTempId(entity1, entity2);
            expect(result).toBe(true);
        });

        it('should return false for different ids', () => {
            const entity1 = { id: 1 };
            const entity2 = { id: 2 };
            const result = service.isSameEntityWithTempId(entity1, entity2);
            expect(result).toBe(false);
        });

        it('should return false when one is undefined', () => {
            const entity = { id: 1 };
            const result = service.isSameEntityWithTempId(entity, undefined);
            expect(result).toBe(false);
        });

        it('should return true when both are undefined', () => {
            // When both are undefined, they are considered the same (undefined === undefined)
            const result = service.isSameEntityWithTempId(undefined, undefined);
            expect(result).toBe(true);
        });
    });
});
