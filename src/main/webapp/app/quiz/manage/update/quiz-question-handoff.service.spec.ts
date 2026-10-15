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
        service.offer([question('a'), question('b')]);

        expect(service.take().map((q) => q.id)).toEqual(['a', 'b']);
        expect(service.take()).toEqual([]);
    });

    it('should replace earlier offers', () => {
        service.offer([question('a')]);
        service.offer([question('b')]);

        expect(service.take().map((q) => q.id)).toEqual(['b']);
    });

    it('should return a copy that later offers do not change', () => {
        const offered = [question('a')];
        service.offer(offered);
        offered.push(question('b'));

        expect(service.take()).toHaveLength(1);
    });
});
