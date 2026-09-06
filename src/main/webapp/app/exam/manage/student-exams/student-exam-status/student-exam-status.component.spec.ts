import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StudentExamStatusComponent } from './student-exam-status.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { By } from '@angular/platform-browser';

describe('StudentExamStatusComponent', () => {
    let component: StudentExamStatusComponent;
    let fixture: ComponentFixture<StudentExamStatusComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [StudentExamStatusComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(StudentExamStatusComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display test exam status when isTestExam is true', () => {
        fixture.componentRef.setInput('isTestExam', true);
        fixture.componentRef.setInput('hasStudentsWithoutExam', false);
        fixture.detectChanges();

        const tag = fixture.debugElement.query(By.css('p-tag'));
        expect(tag).not.toBeNull();
        expect(tag.nativeElement.textContent).toContain('artemisApp.studentExams.studentExamStatusTestExam');
        const icon = fixture.debugElement.query(By.css('.pi-info-circle'));
        expect(icon).not.toBeNull();
    });

    it('should display warning status when not test exam and hasStudentsWithoutExam is true', () => {
        fixture.componentRef.setInput('isTestExam', false);
        fixture.componentRef.setInput('hasStudentsWithoutExam', true);
        fixture.detectChanges();

        const tag = fixture.debugElement.query(By.css('p-tag'));
        expect(tag).not.toBeNull();
        expect(tag.nativeElement.textContent).toContain('artemisApp.studentExams.studentExamStatusWarning');
        const icon = fixture.debugElement.query(By.css('.pi-exclamation-triangle'));
        expect(icon).not.toBeNull();
    });

    it('should display success status when not test exam and hasStudentsWithoutExam is false', () => {
        fixture.componentRef.setInput('isTestExam', false);
        fixture.componentRef.setInput('hasStudentsWithoutExam', false);
        fixture.detectChanges();

        const tag = fixture.debugElement.query(By.css('p-tag'));
        expect(tag).not.toBeNull();
        expect(tag.nativeElement.textContent).toContain('artemisApp.studentExams.studentExamStatusSuccess');
        const icon = fixture.debugElement.query(By.css('.pi-check'));
        expect(icon).not.toBeNull();
    });
});
