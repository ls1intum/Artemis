import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { PlagiarismCaseHeaderComponent } from './plagiarism-case-header.component';
import { PlagiarismCase } from 'app/plagiarism/shared/entities/PlagiarismCase';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('plagiarism case header', () => {
    beforeEach(() => TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] }));
    it('renders the student and exercise from the current case input', () => {
        const fixture = TestBed.createComponent(PlagiarismCaseHeaderComponent);
        fixture.componentRef.setInput('plagiarismCase', {
            student: { name: 'Student', login: 'student' },
            exercise: { title: 'Exercise', type: ExerciseType.TEXT },
        } as PlagiarismCase);
        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toContain('Student (student)');
        expect(fixture.nativeElement.textContent).toContain('Exercise');
        fixture.componentRef.setInput('plagiarismCase', { student: { name: 'Other', login: 'other' } } as PlagiarismCase);
        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toContain('Other (other)');
        expect(fixture.nativeElement.textContent).not.toContain('Exercise');
    });
});
