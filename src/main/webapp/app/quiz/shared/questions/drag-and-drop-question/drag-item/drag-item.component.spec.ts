import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { DragItemComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-item/drag-item.component';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { DragItem } from '../../../entities/drag-item.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('DragItemComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<DragItemComponent>;
    let comp: DragItemComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [DragDropModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(DragItemComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('dragItem', new DragItem());
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });
});
