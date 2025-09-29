import { Component, ViewEncapsulation, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { DragAndDropQuestionUtil } from 'app/quiz/shared/service/drag-and-drop-question-util.service';
import { polyfill } from 'mobile-drag-drop';
import { scrollBehaviourDragImageTranslateOverride } from 'mobile-drag-drop/scroll-behaviour';
import { ImageComponent } from 'app/shared/image/image.component';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { QuizQuestion, RenderedQuizQuestionMarkDownElement } from 'app/quiz/shared/entities/quiz-question.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { faExclamationCircle, faExclamationTriangle, faQuestionCircle, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { CdkDragDrop, CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { NgClass, NgStyle } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbPopover, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { QuizScoringInfoStudentModalComponent } from '../quiz-scoring-infostudent-modal/quiz-scoring-info-student-modal.component';
import { DragItemComponent } from './drag-item/drag-item.component';
import { addPublicFilePrefix } from 'app/app.constants';

// options are optional ;)
polyfill({
    // use this to make use of the scroll behavior
    dragImageTranslateOverride: scrollBehaviourDragImageTranslateOverride,
});

// Drag-enter listener for mobile devices: without this code, mobile drag and drop will not work correctly!
// eslint-disable-next-line @typescript-eslint/no-unused-expressions
(event: any) => {
    event.preventDefault();
};
window.addEventListener('touchmove', () => {}, { passive: false });

enum MappingResult {
    MAPPED_CORRECT,
    MAPPED_INCORRECT,
    NOT_MAPPED,
}
@Component({
    selector: 'jhi-drag-and-drop-question',
    templateUrl: './drag-and-drop-question.component.html',
    providers: [DragAndDropQuestionUtil],
    styleUrls: ['./drag-and-drop-question.component.scss', '../../../overview/participation/quiz-participation.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [
        NgClass,
        FaIconComponent,
        TranslateDirective,
        NgbPopover,
        QuizScoringInfoStudentModalComponent,
        CdkDropListGroup,
        ImageComponent,
        CdkDropList,
        NgStyle,
        DragItemComponent,
        NgbTooltip,
    ],
})
export class DragAndDropQuestionComponent {
    private artemisMarkdown = inject(ArtemisMarkdownService);
    private dragAndDropQuestionUtil = inject(DragAndDropQuestionUtil);

    protected readonly faSpinner = faSpinner;
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly faExclamationCircle = faExclamationCircle;

    readonly MappingResult = MappingResult;

    protected readonly addPublicFilePrefix = addPublicFilePrefix;

    /** needed to trigger a manual reload of the drag and drop background picture */
    readonly secureImageComponent = viewChild.required(ImageComponent);

    question = input.required<QuizQuestion>();
    dragAndDropQuestion = computed(() => this.question() as DragAndDropQuestion);

    // TODO: Map vs. Array --> consistency
    mappings = input<DragAndDropMapping[]>([]);
    _mappings: DragAndDropMapping[] = [];
    clickDisabled = input<boolean>(false);
    showResult = input<boolean>(false);
    questionIndex = input<number>(0);
    score = input<number>(0);

    forceSampleSolution = input<boolean>(false);

    onMappingUpdate = input<any>();
    filePreviewPaths = input<Map<string, string>>(new Map<string, string>());

    mappingsChange = output<DragAndDropMapping[]>();

    showingSampleSolution = signal(false);
    renderedQuestion: RenderedQuizQuestionMarkDownElement;
    sampleSolutionMappings = new Array<DragAndDropMapping>();
    dropAllowed = false;
    correctAnswer: number;
    incorrectLocationMappings: number;
    mappedLocations: number;

    loadingState = 'loading';

    constructor() {
        effect(() => {
            const question = this.dragAndDropQuestion();
            const forced = this.forceSampleSolution();

            if (!question) {
                this.hideSampleSolution();
                return;
            }

            if (forced) {
                this.showSampleSolution();
            } else {
                this.hideSampleSolution();
            }
        });

        effect(() => {
            const question = this.dragAndDropQuestion();
            if (!question) {
                return;
            }
            this.watchCollection();
        });

        effect(() => {
            const q = this.dragAndDropQuestion();
            this.mappings();
            if (!q) return;

            this.evaluateDropLocations();
        });
    }

    watchCollection() {
        // update html for text, hint and explanation for the question
        this.renderedQuestion = new RenderedQuizQuestionMarkDownElement();
        this.renderedQuestion.text = this.artemisMarkdown.safeHtmlForMarkdown(this.dragAndDropQuestion().text);
        this.renderedQuestion.hint = this.artemisMarkdown.safeHtmlForMarkdown(this.dragAndDropQuestion().hint);
        this.renderedQuestion.explanation = this.artemisMarkdown.safeHtmlForMarkdown(this.dragAndDropQuestion().explanation);
    }

    /**
     * Handles drag-available UI
     */
    drag() {
        this.dropAllowed = true;
    }

    /**
     * Handles drag-available UI
     */
    drop() {
        this.dropAllowed = false;
    }

    /** Sets the view displayed to the user
     * @param {Output} value -> loading: background picture for drag and drop question is currently loading
     *                          success: background picture for drag and drop question was loaded
     *                          error: an error occurred during background download */
    changeLoading(value: string) {
        this.loadingState = value;
    }

    /**
     * Prevent scrolling when dragging elements on mobile devices
     * @param event
     */
    preventDefault(event: any) {
        event.mouseEvent.preventDefault();
        return false;
    }

    /**
     * react to the drop event of a drag item
     *
     * @param dropLocation {object | undefined} the dropLocation that the drag item was dropped on.
     *                     May be undefined if drag item was dragged back to the unassigned items.
     * @param dropEvent {object} an event containing the drag item that was dropped
     */
    onDragDrop(dropLocation: DropLocation | undefined, dropEvent: CdkDragDrop<DragItem, DragItem>) {
        this.drop();
        const dragItem = dropEvent.item.data as DragItem;

        if (dropLocation) {
            // check if this mapping is new
            if (this.dragAndDropQuestionUtil.isMappedTogether(this.mappings(), dragItem, dropLocation)) {
                // Do nothing
                this._mappings = this.mappings();
                return;
            }

            // remove existing mappings that contain the drop location or drag item and save their old partners
            let oldDragItem;
            let oldDropLocation;
            this._mappings = this.mappings().filter(function (mapping) {
                if (this.dragAndDropQuestionUtil.isSameEntityWithTempId(dropLocation, mapping.dropLocation)) {
                    oldDragItem = mapping.dragItem;
                    return false;
                }
                if (this.dragAndDropQuestionUtil.isSameEntityWithTempId(dragItem, mapping.dragItem)) {
                    oldDropLocation = mapping.dropLocation;
                    return false;
                }
                return true;
            }, this);

            // add new mapping
            this._mappings.push(new DragAndDropMapping(dragItem, dropLocation));

            // map oldDragItem and oldDropLocation, if they exist
            // this flips positions of drag items when a drag item is dropped on a drop location with an existing drag item
            if (oldDragItem && oldDropLocation) {
                this._mappings.push(new DragAndDropMapping(oldDragItem, oldDropLocation));
            }
        } else {
            const lengthBefore = this.mappings().length;
            // remove existing mapping that contains the drag item
            this._mappings = this.mappings().filter(function (mapping) {
                return !this.dragAndDropQuestionUtil.isSameEntityWithTempId(mapping.dragItem, dragItem);
            }, this);
            if (this._mappings.length === lengthBefore) {
                // nothing changed => return here to skip calling this.onMappingUpdate()
                return;
            }
        }
        this.mappingsChange.emit(this._mappings);

        /** Only execute the onMappingUpdate function if we received such input **/
        const onMappingUpdateFn = this.onMappingUpdate();
        if (onMappingUpdateFn && typeof onMappingUpdateFn === 'function') {
            onMappingUpdateFn();
        }
    }

    /**
     * Get the drag item that was mapped to the given drop location
     *
     * @param dropLocation the drop location that the drag item should be mapped to
     * @return the mapped drag item, or undefined, if no drag item has been mapped to this location
     */
    dragItemForDropLocation(dropLocation: DropLocation) {
        if (this.mappings()) {
            const mapping = this.mappings().find((localMapping) => this.dragAndDropQuestionUtil.isSameEntityWithTempId(localMapping.dropLocation, dropLocation));
            if (mapping) {
                return mapping.dragItem;
            } else {
                return undefined;
            }
        }
        return undefined;
    }

    invalidDragItemForDropLocation(dropLocation: DropLocation) {
        const item = this.dragItemForDropLocation(dropLocation);
        return item ? item.invalid : false;
    }

    /**
     * Get all drag items that have not been assigned to a drop location yet
     *
     * @returnan array of all unassigned drag items
     */
    getUnassignedDragItems() {
        return this.dragAndDropQuestion().dragItems?.filter((dragItem) => {
            return !this.mappings()?.some((mapping) => {
                return this.dragAndDropQuestionUtil.isSameEntityWithTempId(mapping.dragItem, dragItem);
            }, this);
        }, this);
    }

    /**
     * Check if the assigned drag item from the given location is correct
     * (Only possible if this.question.correctMappings is available)
     *
     * @param dropLocation {object} the drop location to check for correctness
     * @return {MappingResult} MAPPED_CORRECT, if the drop location is correct, MAPPED_INCORRECT if not and NOT_MAPPED if the location is correctly left blank
     */
    isLocationCorrect(dropLocation: DropLocation): MappingResult {
        if (!this.dragAndDropQuestion().correctMappings) {
            return MappingResult.MAPPED_INCORRECT;
        }
        const validDragItems = this.dragAndDropQuestion()
            .correctMappings!.filter(function (mapping) {
                return this.dragAndDropQuestionUtil.isSameEntityWithTempId(mapping.dropLocation, dropLocation);
            }, this)
            .map(function (mapping) {
                return mapping.dragItem;
            });
        const selectedItem = this.dragItemForDropLocation(dropLocation);

        if (!selectedItem) {
            return validDragItems.length === 0 ? MappingResult.NOT_MAPPED : MappingResult.MAPPED_INCORRECT;
        } else {
            return validDragItems.some(function (dragItem) {
                return this.dragAndDropQuestionUtil.isSameEntityWithTempId(dragItem, selectedItem);
            }, this)
                ? MappingResult.MAPPED_CORRECT
                : MappingResult.MAPPED_INCORRECT;
        }
    }

    /**
     * Check if there is a drag item assigned to the given location in the solution of the question
     * (Only possible if this.question.correctMappings is available)
     *
     * @param dropLocation {object} the drop location to check for mapping
     * @return {boolean} true, if the drop location is part of a mapping, otherwise false.
     */

    isAssignedLocation(dropLocation: DropLocation): boolean {
        if (!this.dragAndDropQuestion().correctMappings) {
            return false;
        }
        return this.dragAndDropQuestion().correctMappings!.some((mapping) => this.dragAndDropQuestionUtil.isSameEntityWithTempId(dropLocation, mapping.dropLocation));
    }

    /**
     * Display a sample solution instead of the student's answer
     */
    showSampleSolution() {
        this.sampleSolutionMappings = this.dragAndDropQuestionUtil.solve(this.dragAndDropQuestion(), this.mappings());
        this.showingSampleSolution.set(true);
    }

    /**
     * Display the student's answer again
     */
    hideSampleSolution() {
        this.showingSampleSolution.set(false);
    }

    /**
     * Get the drag item that was mapped to the given drop location in the sample solution
     *
     * @param dropLocation the drop location that the drag item should be mapped to
     * @return the mapped drag item, or undefined, if no drag item has been mapped to this location
     */
    correctDragItemForDropLocation(dropLocation: DropLocation) {
        const dragAndDropQuestionUtil = this.dragAndDropQuestionUtil;
        const mapping = this.sampleSolutionMappings.find(function (solutionMapping) {
            return dragAndDropQuestionUtil.isSameEntityWithTempId(solutionMapping.dropLocation, dropLocation);
        });
        return mapping?.dragItem;
    }

    /**
     * Count and assign the amount of right mappings, incorrect mappings and the number of drop locations participating in at least one mapping for a question
     * by using the isLocationCorrect Method and the isAssignedLocation Method
     */
    evaluateDropLocations(): void {
        if (this.dragAndDropQuestion().dropLocations) {
            this.correctAnswer = this.dragAndDropQuestion().dropLocations!.filter((dropLocation) => this.isLocationCorrect(dropLocation) === MappingResult.MAPPED_CORRECT).length;
            this.incorrectLocationMappings = this.dragAndDropQuestion().dropLocations!.filter(
                (dropLocation) => this.isLocationCorrect(dropLocation) === MappingResult.MAPPED_INCORRECT,
            ).length;
            this.mappedLocations = this.dragAndDropQuestion().dropLocations!.filter((dropLocation) => this.isAssignedLocation(dropLocation)).length;
        }
    }
}
