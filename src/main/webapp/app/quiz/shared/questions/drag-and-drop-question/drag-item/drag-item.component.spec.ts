import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DragItemComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-item/drag-item.component';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { DragItem } from '../../../entities/drag-item.model';

describe('DragItemComponent', () => {
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
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });
});
