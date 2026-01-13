import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { PdfUploadTarget, PdfUploadTargetDialogComponent } from './pdf-upload-target-dialog.component';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { Subject } from 'rxjs';

describe('PdfUploadTargetDialogComponent', () => {
    setupTestBed({ zoneless: true });

    let component: PdfUploadTargetDialogComponent;
    let fixture: ComponentFixture<PdfUploadTargetDialogComponent>;
    let dialogRef: DynamicDialogRef;
    let dialogRefCloseSpy: ReturnType<typeof vi.fn>;

    beforeEach(async () => {
        dialogRefCloseSpy = vi.fn();
        dialogRef = {
            close: dialogRefCloseSpy,
            onClose: new Subject<any>(),
        } as unknown as DynamicDialogRef;

        await TestBed.configureTestingModule({
            imports: [PdfUploadTargetDialogComponent],
            providers: [
                { provide: DynamicDialogRef, useValue: dialogRef },
                {
                    provide: DynamicDialogConfig,
                    useValue: {
                        data: {
                            lectures: [],
                            uploadedFiles: [],
                        },
                    },
                },
            ],
        })
            .overrideComponent(PdfUploadTargetDialogComponent, {
                remove: { imports: [ArtemisTranslatePipe, TranslateDirective] },
                add: { imports: [MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(PdfUploadTargetDialogComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('initialization', () => {
        it('should create with default values', () => {
            expect(component).toBeTruthy();
            expect(component.targetType()).toBe('new');
            expect(component.selectedLectureId()).toBeUndefined();
            expect(component.newLectureTitle()).toBe('');
            expect(component.lectures()).toEqual([]);
            expect(component.uploadedFiles()).toEqual([]);
        });
    });

    describe('initializeWithFiles', () => {
        it('should set uploaded files and derive title from first filename', () => {
            const files = [new File(['content'], 'Chapter_01_Introduction.pdf', { type: 'application/pdf' })];

            (component as any).initializeWithFiles(files);

            expect(component.uploadedFiles()).toEqual(files);
            expect(component.newLectureTitle()).toBe('Chapter 01 Introduction');
        });

        it('should handle multiple files and use first filename for title', () => {
            const files = [new File(['content1'], 'First_File.pdf', { type: 'application/pdf' }), new File(['content2'], 'Second_File.pdf', { type: 'application/pdf' })];

            (component as any).initializeWithFiles(files);

            expect(component.uploadedFiles()).toHaveLength(2);
            expect(component.newLectureTitle()).toBe('First File');
        });

        it('should not set title when no files provided', () => {
            (component as any).initializeWithFiles([]);

            expect(component.uploadedFiles()).toEqual([]);
            expect(component.newLectureTitle()).toBe('');
        });

        it('should clean up filename with dashes', () => {
            const files = [new File(['content'], 'lecture-notes-week-5.pdf', { type: 'application/pdf' })];

            (component as any).initializeWithFiles(files);

            expect(component.newLectureTitle()).toBe('lecture notes week 5');
        });

        it('should handle uppercase PDF extension', () => {
            const files = [new File(['content'], 'MyLecture.PDF', { type: 'application/pdf' })];

            (component as any).initializeWithFiles(files);

            expect(component.newLectureTitle()).toBe('MyLecture');
        });

        it('should trim whitespace from derived title', () => {
            const files = [new File(['content'], '  spaced_name  .pdf', { type: 'application/pdf' })];

            (component as any).initializeWithFiles(files);

            expect(component.newLectureTitle()).toBe('spaced name');
        });

        it('should collapse multiple spaces in derived title', () => {
            const files = [new File(['content'], 'file__with___many_spaces.pdf', { type: 'application/pdf' })];

            (component as any).initializeWithFiles(files);

            expect(component.newLectureTitle()).toBe('file with many spaces');
        });
    });

    describe('onTargetTypeChange', () => {
        it('should set target type to new and clear selected lecture', () => {
            component.selectedLectureId.set(123);

            component.onTargetTypeChange('new');

            expect(component.targetType()).toBe('new');
            expect(component.selectedLectureId()).toBeUndefined();
        });

        it('should set target type to existing without clearing lecture id', () => {
            component.selectedLectureId.set(123);

            component.onTargetTypeChange('existing');

            expect(component.targetType()).toBe('existing');
            expect(component.selectedLectureId()).toBe(123);
        });
    });

    describe('onLectureSelect', () => {
        it('should set selected lecture id from select event', () => {
            const event = { target: { value: '42' } } as unknown as Event;

            component.onLectureSelect(event);

            expect(component.selectedLectureId()).toBe(42);
        });

        it('should set undefined when empty value selected', () => {
            component.selectedLectureId.set(123);
            const event = { target: { value: '' } } as unknown as Event;

            component.onLectureSelect(event);

            expect(component.selectedLectureId()).toBeUndefined();
        });
    });

    describe('isValid', () => {
        describe('when target type is new', () => {
            beforeEach(() => {
                component.onTargetTypeChange('new');
            });

            it('should return true when title is not empty', () => {
                component.newLectureTitle.set('My Lecture');

                expect(component.isValid()).toBe(true);
            });

            it('should return false when title is empty', () => {
                component.newLectureTitle.set('');

                expect(component.isValid()).toBe(false);
            });

            it('should return false when title contains only whitespace', () => {
                component.newLectureTitle.set('   ');

                expect(component.isValid()).toBe(false);
            });
        });

        describe('when target type is existing', () => {
            beforeEach(() => {
                component.onTargetTypeChange('existing');
            });

            it('should return true when lecture is selected', () => {
                component.selectedLectureId.set(42);

                expect(component.isValid()).toBe(true);
            });

            it('should return false when no lecture is selected', () => {
                expect(component.isValid()).toBe(false);
            });
        });
    });

    describe('confirm', () => {
        it('should close dialog with new lecture result when valid', () => {
            component.onTargetTypeChange('new');
            component.newLectureTitle.set('My New Lecture');

            component.confirm();

            expect(dialogRefCloseSpy).toHaveBeenCalledTimes(1);
            const result = dialogRefCloseSpy.mock.calls[0][0] as PdfUploadTarget;
            expect(result.targetType).toBe('new');
            expect(result.newLectureTitle).toBe('My New Lecture');
            expect(result.lectureId).toBeUndefined();
        });

        it('should close dialog with existing lecture result when valid', () => {
            component.onTargetTypeChange('existing');
            component.selectedLectureId.set(99);

            component.confirm();

            expect(dialogRefCloseSpy).toHaveBeenCalledTimes(1);
            const result = dialogRefCloseSpy.mock.calls[0][0] as PdfUploadTarget;
            expect(result.targetType).toBe('existing');
            expect(result.lectureId).toBe(99);
            expect(result.newLectureTitle).toBeUndefined();
        });

        it('should trim lecture title in result', () => {
            component.onTargetTypeChange('new');
            component.newLectureTitle.set('  Trimmed Title  ');

            component.confirm();

            const result = dialogRefCloseSpy.mock.calls[0][0] as PdfUploadTarget;
            expect(result.newLectureTitle).toBe('Trimmed Title');
        });

        it('should not close dialog when invalid', () => {
            component.onTargetTypeChange('new');
            component.newLectureTitle.set('');

            component.confirm();

            expect(dialogRefCloseSpy).not.toHaveBeenCalled();
        });

        it('should not close dialog when existing type but no lecture selected', () => {
            component.onTargetTypeChange('existing');

            component.confirm();

            expect(dialogRefCloseSpy).not.toHaveBeenCalled();
        });
    });

    describe('cancel', () => {
        it('should close dialog without result', () => {
            component.cancel();

            expect(dialogRefCloseSpy).toHaveBeenCalledTimes(1);
            expect(dialogRefCloseSpy).toHaveBeenCalledWith();
        });
    });

    describe('lectures signal', () => {
        it('should allow setting lectures array', () => {
            const lecture1 = new Lecture();
            lecture1.id = 1;
            lecture1.title = 'Lecture 1';
            const lecture2 = new Lecture();
            lecture2.id = 2;
            lecture2.title = 'Lecture 2';

            component.lectures.set([lecture1, lecture2]);

            expect(component.lectures()).toHaveLength(2);
            expect(component.lectures()[0].title).toBe('Lecture 1');
            expect(component.lectures()[1].title).toBe('Lecture 2');
        });
    });
});
