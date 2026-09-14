import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExamModeBadgeComponent } from 'app/exam/shared/exam-mode-badge/exam-mode-badge.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { faGraduationCap, faVial } from '@fortawesome/free-solid-svg-icons';

describe('ExamModeBadgeComponent', () => {
    let fixture: ComponentFixture<ExamModeBadgeComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamModeBadgeComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamModeBadgeComponent);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should display test exam badge when testExam is true', () => {
        fixture.componentRef.setInput('testExam', true);
        fixture.detectChanges();

        const tag = fixture.debugElement.query(By.css('[data-testid="exam-mode-tag"]'));
        expect(tag).not.toBeNull();
        expect(tag.nativeElement.textContent).toContain('artemisApp.examManagement.testExam.testExam');
        const icon = fixture.debugElement.query(By.css('fa-icon'));
        expect(icon).not.toBeNull();
        expect(icon.componentInstance.icon()).toBe(faVial);
    });

    it('should display real exam badge when testExam is false', () => {
        fixture.componentRef.setInput('testExam', false);
        fixture.detectChanges();

        const tag = fixture.debugElement.query(By.css('[data-testid="exam-mode-tag"]'));
        expect(tag).not.toBeNull();
        expect(tag.nativeElement.textContent).toContain('artemisApp.examManagement.testExam.realExam');
        const icon = fixture.debugElement.query(By.css('fa-icon'));
        expect(icon).not.toBeNull();
        expect(icon.componentInstance.icon()).toBe(faGraduationCap);
    });
});
