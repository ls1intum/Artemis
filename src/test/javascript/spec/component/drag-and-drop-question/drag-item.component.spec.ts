import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DragItemComponent } from 'app/exercises/quiz/shared/questions/drag-and-drop-question/drag-item.component';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { ArtemisTestModule } from '../../test.module';
import { FitTextModule } from 'app/exercises/quiz/shared/fit-text/fit-text.module';

describe('DragItemComponent', () => {
    let fixture: ComponentFixture<DragItemComponent>;
    let comp: DragItemComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, DragDropModule, FitTextModule],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DragItemComponent);
                comp = fixture.componentInstance;
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
