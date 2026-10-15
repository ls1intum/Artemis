import { describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { QuizQuestionPoolCreateQuizDialogComponent } from './quiz-question-pool-create-quiz-dialog.component';
import { PoolLectureUnit, PoolSelectionCandidate, PoolSelectionRequest } from 'app/hyperion/manage/quiz-question-pool/quiz-question-pool.types';

describe('QuizQuestionPoolCreateQuizDialogComponent', () => {
    let fixture: ComponentFixture<QuizQuestionPoolCreateQuizDialogComponent>;
    let component: QuizQuestionPoolCreateQuizDialogComponent;

    const competencies = [
        { id: 9, title: 'Arrays' },
        { id: 10, title: 'Loops' },
    ];

    function unit(competencyId: number, lectureUnitId: number, name: string): PoolLectureUnit {
        return {
            competencyId,
            competencyTitle: 'C',
            lectureUnitId,
            lectureUnitName: name,
            lectureTitle: 'Lecture 1',
            excluded: false,
            materialChanged: false,
            questionCount: 1,
            approvedCount: 1,
        };
    }

    function candidate(competencyId: number, lectureUnitId: number, overrides: Partial<PoolSelectionCandidate> = {}): PoolSelectionCandidate {
        return { competencyId, lectureUnitId, questionType: 'single-choice', difficulty: 'MEDIUM', count: 1, ...overrides };
    }

    const lectureUnits = [unit(9, 90, 'Unit 9'), unit(10, 100, 'Unit 10'), unit(10, 110, 'Unit 11')];
    const candidates = [candidate(9, 90), candidate(9, 90, { questionType: 'multiple-choice', difficulty: 'HARD' }), candidate(10, 100, { count: 2 })];

    function setup(initialCompetencyId?: number): void {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(QuizQuestionPoolCreateQuizDialogComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('visible', true);
        fixture.componentRef.setInput('competencies', competencies);
        fixture.componentRef.setInput('lectureUnits', lectureUnits);
        fixture.componentRef.setInput('candidates', candidates);
        fixture.componentRef.setInput('initialCompetencyId', initialCompetencyId);
        fixture.detectChanges();
    }

    it('should start from every competency with all units, types and difficulties and count the matching questions', () => {
        setup();

        expect(component.selectedCompetencyIds()).toEqual([9, 10]);
        expect(component.selectedLectureUnitIds()).toEqual([]);
        expect(component.count()).toBe(10);
        expect(component.unitOptions().map((option) => option.value)).toEqual([90, 100, 110]);
        expect(component.typeOptions().map((option) => option.value)).toEqual(['single-choice', 'multiple-choice']);
        expect(component.difficultyOptions().map((option) => option.value)).toEqual(['MEDIUM', 'HARD']);
        expect(component.availableCount()).toBe(4);
        expect(component.isValid()).toBe(true);
    });

    it('should start from the competency the page filters by', () => {
        setup(10);

        expect(component.selectedCompetencyIds()).toEqual([10]);
        expect(component.unitOptions().map((option) => option.value)).toEqual([100, 110]);
        expect(component.availableCount()).toBe(2);
    });

    it('should drop lecture units that no longer belong to a chosen competency and narrow the count by the choices', () => {
        setup();
        component.selectedLectureUnitIds.set([90, 100]);

        component.setCompetencies([9]);

        expect(component.selectedLectureUnitIds()).toEqual([90]);
        component.selectedTypes.set(['multiple-choice']);
        expect(component.availableCount()).toBe(1);
        component.selectedDifficulties.set(['MEDIUM']);
        expect(component.availableCount()).toBe(0);
    });

    it('should emit the request with absent filters for unrestricted dimensions', () => {
        setup();
        const requests: PoolSelectionRequest[] = [];
        component.create.subscribe((request) => requests.push(request));
        component.count.set(4);

        component.submit();

        expect(requests).toEqual([{ competencyIds: [9, 10], lectureUnitIds: undefined, count: 4, questionTypes: undefined, difficulties: undefined }]);
    });

    it('should emit the chosen filters and refuse an invalid request', () => {
        setup();
        const requests: PoolSelectionRequest[] = [];
        component.create.subscribe((request) => requests.push(request));
        component.setCompetencies([9]);
        component.selectedLectureUnitIds.set([90]);
        component.selectedTypes.set(['single-choice']);
        component.selectedDifficulties.set(['MEDIUM']);

        component.submit();
        expect(requests).toEqual([{ competencyIds: [9], lectureUnitIds: [90], count: 10, questionTypes: ['single-choice'], difficulties: ['MEDIUM'] }]);

        component.count.set(0);
        expect(component.isValid()).toBe(false);
        component.submit();
        expect(requests).toHaveLength(1);
    });

    it('should close on cancel and reset when reopened', () => {
        setup();
        const visibleSpy = vi.fn();
        component.visible.subscribe(visibleSpy);
        component.setCompetencies([9]);
        component.count.set(3);

        component.cancel();
        expect(visibleSpy).toHaveBeenCalledWith(false);

        fixture.componentRef.setInput('visible', false);
        fixture.detectChanges();
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();

        expect(component.selectedCompetencyIds()).toEqual([9, 10]);
        expect(component.count()).toBe(10);
    });
});
