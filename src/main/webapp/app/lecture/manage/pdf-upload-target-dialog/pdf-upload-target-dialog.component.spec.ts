import { ComponentFixture, TestBed } from '@angular/core/testing';
import { type Mock, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { PdfUploadTarget, PdfUploadTargetDialogComponent } from './pdf-upload-target-dialog.component';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('PdfUploadTargetDialogComponent', () => {
    let component: PdfUploadTargetDialogComponent;
    let fixture: ComponentFixture<PdfUploadTargetDialogComponent>;
    let targetSelectedSpy: Mock<(target: PdfUploadTarget) => void>;

    const lectures = [{ id: 1, title: 'Introduction' } as Lecture, { id: 2, title: 'Testing' } as Lecture];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PdfUploadTargetDialogComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(PdfUploadTargetDialogComponent);
        component = fixture.componentInstance;
        targetSelectedSpy = vi.fn();
        component.targetSelected.subscribe(targetSelectedSpy);
    });

    afterEach(() => {
        fixture.destroy();
        vi.restoreAllMocks();
    });

    function dropFiles(...names: string[]): void {
        fixture.componentRef.setInput(
            'uploadedFiles',
            names.map((name) => new File([''], name, { type: 'application/pdf' })),
        );
    }

    it('should start with a new lecture as the target', () => {
        expect(component.targetType()).toBe('new');
        expect(component.selectedLectureId()).toBeUndefined();
        expect(component.newLectureTitle()).toBe('');
        expect(component.isValid()).toBe(false);
    });

    describe('derived lecture title', () => {
        it.each([
            [['Introduction.pdf'], 'Introduction'],
            [['Lecture_01.pdf', 'Lecture_02.pdf'], 'Lecture 01'],
            [['Software-Engineering_Basics.pdf'], 'Software Engineering Basics'],
            [['Upper.PDF'], 'Upper'],
            [['  padded .pdf'], 'padded'],
            [['a__-__b.pdf'], 'a b'],
        ])('should derive the title from %s', (names, expectedTitle) => {
            dropFiles(...names);

            expect(component.newLectureTitle()).toBe(expectedTitle);
        });

        it('should start over when other files are dropped', () => {
            fixture.componentRef.setInput('lectures', lectures);
            dropFiles('First.pdf');
            component.onTargetTypeChange('existing');
            component.selectedLectureId.set(2);
            component.newLectureTitle.set('Edited title');

            dropFiles('Second.pdf');

            expect(component.targetType()).toBe('new');
            expect(component.selectedLectureId()).toBeUndefined();
            expect(component.newLectureTitle()).toBe('Second');
        });
    });

    describe('target', () => {
        it('should clear the selected lecture when switching back to a new lecture', () => {
            component.onTargetTypeChange('existing');
            component.selectedLectureId.set(2);

            component.onTargetTypeChange('new');

            expect(component.targetType()).toBe('new');
            expect(component.selectedLectureId()).toBeUndefined();
        });

        it('should keep the selected lecture when switching to an existing lecture', () => {
            component.selectedLectureId.set(2);

            component.onTargetTypeChange('existing');

            expect(component.selectedLectureId()).toBe(2);
        });

        it.each([
            ['Intro', true],
            ['', false],
            ['   ', false],
        ])('should accept the new lecture title "%s": %s', (title, valid) => {
            component.newLectureTitle.set(title);

            expect(component.isValid()).toBe(valid);
        });

        it('should require a selected lecture for an existing lecture', () => {
            component.onTargetTypeChange('existing');
            expect(component.isValid()).toBe(false);

            component.selectedLectureId.set(1);
            expect(component.isValid()).toBe(true);
        });
    });

    describe('confirm', () => {
        it('should emit a new lecture with the trimmed title and close', () => {
            component.visible.set(true);
            component.newLectureTitle.set('  New Lecture  ');

            component.confirm();

            expect(targetSelectedSpy).toHaveBeenCalledExactlyOnceWith({ targetType: 'new', lectureId: undefined, newLectureTitle: 'New Lecture' } satisfies PdfUploadTarget);
            expect(component.visible()).toBe(false);
        });

        it('should emit the selected existing lecture and close', () => {
            component.visible.set(true);
            component.onTargetTypeChange('existing');
            component.selectedLectureId.set(2);

            component.confirm();

            expect(targetSelectedSpy).toHaveBeenCalledExactlyOnceWith({ targetType: 'existing', lectureId: 2, newLectureTitle: undefined } satisfies PdfUploadTarget);
            expect(component.visible()).toBe(false);
        });

        it.each(['new', 'existing'] as const)('should neither emit nor close while the %s target is incomplete', (targetType) => {
            component.visible.set(true);
            component.onTargetTypeChange(targetType);

            component.confirm();

            expect(targetSelectedSpy).not.toHaveBeenCalled();
            expect(component.visible()).toBe(true);
        });
    });

    it('should close without choosing a target on cancel', () => {
        component.visible.set(true);

        component.cancel();

        expect(component.visible()).toBe(false);
        expect(targetSelectedSpy).not.toHaveBeenCalled();
    });

    it('should list the dropped files and offer existing lectures only when there are any', async () => {
        dropFiles('Introduction.pdf', 'Testing.pdf');
        component.visible.set(true);
        fixture.detectChanges();
        await fixture.whenStable();

        const dialog = document.querySelector<HTMLElement>('[data-testid="pdf-upload-target-dialog"]')!;
        expect(dialog.textContent).toContain('Introduction.pdf');
        expect(dialog.textContent).toContain('Testing.pdf');
        expect(dialog.querySelector('#targetExisting')).toBeNull();
        expect(document.querySelector<HTMLInputElement>('#lectureTitleInput')?.value).toBe('Introduction');

        fixture.componentRef.setInput('lectures', lectures);
        fixture.detectChanges();

        expect(dialog.querySelector('#targetExisting')).not.toBeNull();
    });
});
