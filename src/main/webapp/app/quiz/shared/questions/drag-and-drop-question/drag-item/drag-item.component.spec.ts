import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { DragItemComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-item/drag-item.component';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { DragItem } from '../../../entities/drag-item.model';

describe('DragItemComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<DragItemComponent>;
    let comp: DragItemComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [DragDropModule],
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
});
