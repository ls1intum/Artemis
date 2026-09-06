import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { DragItemComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-item/drag-item.component';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { DragItem } from '../../../entities/drag-item.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('DragItemComponent', () => {
    let fixture: ComponentFixture<DragItemComponent>;
    let comp: DragItemComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [DragDropModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DragItemComponent);
                comp = fixture.componentInstance;
                fixture.componentRef.setInput('dragItem', new DragItem());
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });

    it('imageSrc returns undefined when the drag item has no picture', () => {
        fixture.componentRef.setInput('dragItem', new DragItem());
        expect(comp['imageSrc']()).toBeUndefined();
    });

    it('imageSrc prefers the local preview (not-yet-saved upload) when present', () => {
        fixture.componentRef.setInput('dragItem', { id: 2, pictureFilePath: 'stored/path/pic.png' } as DragItem);
        fixture.componentRef.setInput('filePreviewPaths', new Map([['stored/path/pic.png', 'blob:local-preview']]));
        // addPublicFilePrefix passes blob URLs through unchanged
        expect(comp['imageSrc']()).toBe('blob:local-preview');
    });

    it('imageSrc builds the question-scoped url from the question id and drag item id', () => {
        fixture.componentRef.setInput('dragItem', { id: 2, pictureFilePath: 'pic.png' } as DragItem);
        fixture.componentRef.setInput('questionId', 5);
        expect(comp['imageSrc']()).toBe('api/core/files/drag-and-drop/questions/5/drag-items/2/pic.png');
    });

    it('imageSrc builds the same url from a drag item that still carries a whole path', () => {
        fixture.componentRef.setInput('dragItem', { id: 2, pictureFilePath: 'drag-and-drop/drag-items/2/pic.png' } as DragItem);
        fixture.componentRef.setInput('questionId', 5);
        expect(comp['imageSrc']()).toBe('api/core/files/drag-and-drop/questions/5/drag-items/2/pic.png');
    });

    it('imageSrc falls back to the stored path when the question id is missing', () => {
        fixture.componentRef.setInput('dragItem', { id: 2, pictureFilePath: 'pic.png' } as DragItem);
        expect(comp['imageSrc']()).toBe('api/core/files/pic.png');
    });
});
