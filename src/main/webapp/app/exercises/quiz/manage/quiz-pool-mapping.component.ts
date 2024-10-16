import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { faExclamationCircle, faPlus, faTimes } from '@fortawesome/free-solid-svg-icons';
import { QuizGroup } from 'app/entities/quiz/quiz-group.model';
import { Subject } from 'rxjs';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-quiz-pool-mapping',
    templateUrl: './quiz-pool-mapping.component.html',
    styleUrls: ['./quiz-pool-mapping.component.scss'],
})
export class QuizPoolMappingComponent implements OnInit, OnChanges, OnDestroy {
    private alertService = inject(AlertService);

    @Input() quizGroups: QuizGroup[] = [];
    @Input() quizQuestions: QuizQuestion[] = [];
    @Input() disabled = false;

    @Output() onQuizGroupUpdated = new EventEmitter();
    @Output() onQuizQuestionDropped = new EventEmitter();

    quizGroupNameQuestionsMap: Map<string, Array<QuizQuestion>> = new Map();
    unmappedQuizQuestions: QuizQuestion[] = [];

    faPlus = faPlus;
    faTimes = faTimes;
    faExclamationCircle = faExclamationCircle;

    protected dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    ngOnInit(): void {
        this.handleUpdate();
    }

    ngOnChanges(): void {
        this.handleUpdate();
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Set the given quizGroup to the given quizQuestion and emit onQuizQuestionDropped.
     *
     * @param quizQuestion the quiz question that is dropped
     * @param quizGroup the quiz question to which the quiz question is dropped
     */
    handleOnQuizQuestionDropped(quizQuestion: QuizQuestion, quizGroup?: QuizGroup) {
        quizQuestion.quizGroup = quizGroup;
        this.onQuizQuestionDropped.emit();
    }

    /**
     * If QuizGroup with the same name does not exist, create a new QuizGroup object and push to the quizGroups list.
     * Additionally, initialize the quizGroupNameQuestionsMap of the name of the new QuizGroup
     *
     * @param name the name of the new quiz group
     */
    addGroup(name: string) {
        if (name.length == 0) {
            this.alertService.error('artemisApp.quizPool.invalidReasons.groupNameEmpty');
        } else if (name.length > 100) {
            this.alertService.error('artemisApp.quizPool.invalidReasons.groupNameLength');
        } else if (this.quizGroupNameQuestionsMap.has(name)) {
            this.alertService.error('artemisApp.quizPool.invalidReasons.groupSameName');
        } else {
            const quizGroup = new QuizGroup();
            quizGroup.name = name;
            this.quizGroups.push(quizGroup);
            this.quizGroupNameQuestionsMap.set(quizGroup.name, new Array<QuizQuestion>());
            this.onQuizGroupUpdated.emit();
        }
    }

    /**
     * Move all quiz questions of the to be deleted quiz group to unmappedQuizQuestions, then remove the quiz group from the quizGroups list and from quizGroupNameQuestionsMap.
     *
     * @param index the index of the quiz group that is going to be deleted
     */
    deleteGroup(index: number) {
        const quizGroup = this.quizGroups[index];
        for (const quizQuestion of this.quizGroupNameQuestionsMap.get(quizGroup.name)!) {
            this.addQuestion(quizQuestion);
            quizQuestion.quizGroup = undefined;
        }
        this.quizGroups.splice(index, 1);
        this.quizGroupNameQuestionsMap.delete(quizGroup.name);
        this.dialogErrorSource.next('');
        this.onQuizGroupUpdated.emit();
    }

    /**
     * Add given quizQuestion to the unmappedQuizQuestions list.
     *
     * @param quizQuestion the quizQuestion to be added
     */
    addQuestion(quizQuestion: QuizQuestion) {
        this.unmappedQuizQuestions.push(quizQuestion);
    }

    /**
     * If the to be deleted QuizQuestion has a group, update the list of QuizQuestion that belongs to the group.
     * Otherwise, update the unmappedQuizQuestions list.
     *
     * @param quizQuestionToBeDeleted
     */
    deleteQuestion(quizQuestionToBeDeleted: QuizQuestion) {
        const groupName = quizQuestionToBeDeleted.quizGroup?.name;
        if (groupName) {
            const quizQuestions = this.quizGroupNameQuestionsMap.get(groupName);
            const updatedQuizQuestions = quizQuestions!.filter((quizQuestion) => quizQuestion !== quizQuestionToBeDeleted);
            this.quizGroupNameQuestionsMap.set(groupName, updatedQuizQuestions);
        } else {
            this.unmappedQuizQuestions! = this.unmappedQuizQuestions!.filter((quizQuestion) => quizQuestion !== quizQuestionToBeDeleted);
        }
    }

    /**
     * Find list of name of the groups that do not have any questions mapped to it.
     *
     * @returns the list of group name
     */
    getGroupNamesWithNoQuestion(): Array<string> {
        const results = new Array<string>();
        for (const [name, quizQuestions] of this.quizGroupNameQuestionsMap) {
            if (quizQuestions.length == 0) {
                results.push(name);
            }
        }
        return results;
    }

    /**
     * Check if there is a group with no questions mapped to it.
     *
     * @return true if such group exists or false otherwise
     */
    hasGroupsWithNoQuestion(): boolean {
        return this.getGroupNamesWithNoQuestion().length > 0;
    }

    /**
     * Find list of name of the groups that have questions with different points.
     *
     * @returns the list of group name
     */
    getGroupNamesWithDifferentQuestionPoints(): Array<string> {
        const results = new Array<string>();
        for (const [name, quizQuestions] of this.quizGroupNameQuestionsMap) {
            if (!quizQuestions.every((quizQuestion) => quizQuestion.points === quizQuestions[0].points)) {
                results.push(name);
            }
        }
        return results;
    }

    /**
     * Check if there is a group with questions that have different points mapped to it.
     *
     * @return true if such group exists or false otherwise
     */
    hasGroupsWithDifferentQuestionPoints() {
        return this.getGroupNamesWithDifferentQuestionPoints().length > 0;
    }

    /**
     * Calculate the maximum points of quiz questions by summing the points of first question from each group and the points of questions from unmappedQuizQuestions.
     *
     * @return the maximum point
     */
    getMaxPoints(): number {
        let maxPoints = 0;
        for (const quizQuestions of this.quizGroupNameQuestionsMap.values()) {
            if (quizQuestions.length > 0) {
                maxPoints += quizQuestions[0].points ?? 0;
            }
        }
        maxPoints += this.unmappedQuizQuestions.reduce((sum: number, quizQuestion: QuizQuestion) => sum + (quizQuestion.points ?? 0), 0);
        return maxPoints;
    }

    /**
     * Set the quizGroupNameQuestionsMap of each quiz group according to the current quizQuestions.
     */
    handleUpdate() {
        this.quizGroupNameQuestionsMap = new Map<string, Array<QuizQuestion>>();
        for (const quizGroup of this.quizGroups) {
            this.quizGroupNameQuestionsMap.set(quizGroup.name, []);
        }
        this.unmappedQuizQuestions = [];
        for (const quizQuestion of this.quizQuestions) {
            if (quizQuestion.quizGroup) {
                this.quizGroupNameQuestionsMap.get(quizQuestion.quizGroup.name)!.push(quizQuestion);
            } else {
                this.unmappedQuizQuestions.push(quizQuestion);
            }
        }
    }
}
