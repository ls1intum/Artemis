import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output } from '@angular/core';
import { faExclamationCircle, faPlus, faTimes } from '@fortawesome/free-solid-svg-icons';
import { QuizGroup } from 'app/entities/quiz/quiz-group.model';
import { Subject } from 'rxjs';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';

@Component({
    selector: 'jhi-quiz-pool-mapping',
    templateUrl: './quiz-pool-mapping.component.html',
    styleUrls: ['./quiz-pool-mapping.component.scss'],
})
export class QuizPoolMappingComponent implements OnInit, OnChanges, OnDestroy {
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

    handleOnQuizQuestionDropped(quizQuestion: QuizQuestion, quizGroup?: QuizGroup) {
        quizQuestion.quizGroup = quizGroup;
        this.onQuizQuestionDropped.emit();
    }

    addGroup(name: string) {
        if (!this.quizGroupNameQuestionsMap.has(name) && name.length > 0) {
            const quizGroup = new QuizGroup(name);
            this.quizGroups.push(quizGroup);
            this.quizGroupNameQuestionsMap.set(quizGroup.name, new Array<QuizQuestion>());
            this.onQuizGroupUpdated.emit();
        }
    }

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

    addQuestion(quizQuestion: QuizQuestion) {
        this.unmappedQuizQuestions.push(quizQuestion);
    }

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

    getGroupNamesWithNoQuestion(): Array<string> {
        const results = new Array<string>();
        for (const [name, quizQuestions] of this.quizGroupNameQuestionsMap) {
            if (quizQuestions.length == 0) {
                results.push(name);
            }
        }
        return results;
    }

    hasGroupsWithNoQuestion(): boolean {
        return this.getGroupNamesWithNoQuestion().length > 0;
    }

    getGroupNamesWithDifferentQuestionPoints(): Array<string> {
        const results = new Array<string>();
        for (const [name, quizQuestions] of this.quizGroupNameQuestionsMap) {
            if (!quizQuestions.every((quizQuestion) => quizQuestion.points === quizQuestions[0].points)) {
                results.push(name);
            }
        }
        return results;
    }

    hasGroupsWithDifferentQuestionPoints() {
        return this.getGroupNamesWithDifferentQuestionPoints().length > 0;
    }

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
