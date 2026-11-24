import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { QuizQuestionHandoffService } from './quiz-question-handoff.service';
import { GeneratedQuestion } from 'app/hyperion/quiz/quiz-ai-generation-modal/quiz-ai-generation.types';

describe('QuizQuestionHandoffService', () => {
    let service: QuizQuestionHandoffService;

    const question = (id: string): GeneratedQuestion => ({ id, type: 'single-choice', title: id, questionText: 'Q?', options: [{ text: 'A', correct: true }] });

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(QuizQuestionHandoffService);
    });

    it('should hand over the offered questions once', () => {
        service.offer(1, [question('a'), question('b')]);

        expect(service.take(1).map((q) => q.id)).toEqual(['a', 'b']);
        expect(service.take(1)).toEqual([]);
    });

    it('should replace earlier offers', () => {
        service.offer(1, [question('a')]);
        service.offer(1, [question('b')]);

        expect(service.take(1).map((q) => q.id)).toEqual(['b']);
    });

    it('should return a copy that later offers do not change', () => {
        const offered = [question('a')];
        service.offer(1, offered);
        offered.push(question('b'));

        expect(service.take(1)).toHaveLength(1);
    });

    it('should hand nothing to another course and drop the offer', () => {
        service.offer(1, [question('a')]);

        expect(service.take(2)).toEqual([]);
        expect(service.take(1)).toEqual([]);
    });

    it('should drop the offer when cleared', () => {
        service.offer(1, [question('a')]);
        service.clear();

        expect(service.take(1)).toEqual([]);
    });
});
