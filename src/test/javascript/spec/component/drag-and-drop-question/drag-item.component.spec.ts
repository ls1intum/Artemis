import { DragDropModule } from '@angular/cdk/drag-drop';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FitTextModule } from 'app/exercises/quiz/shared/fit-text/fit-text.module';
import { DragItemComponent } from 'app/exercises/quiz/shared/questions/drag-and-drop-question/drag-item.component';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { MockComponent } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';

describe('DragItemComponent', () => {
    let fixture: ComponentFixture<DragItemComponent>;
    let comp: DragItemComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, DragDropModule, FitTextModule],
            declarations: [MockComponent(SecuredImageComponent), DragItemComponent],
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
