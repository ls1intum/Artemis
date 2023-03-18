import { ArtemisTestModule } from '../../test.module';
import { ExerciseCourseTitlePipe } from 'app/shared/pipes/exercise-course-title.pipe';
import { ButtonComponent } from 'app/shared/components/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExerciseImportTabsComponent } from 'app/exercises/shared/import/exercise-import-tabs.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { FormsModule } from '@angular/forms';
import { ExerciseImportFromFileComponent } from 'app/exercises/shared/import/from-file/exercise-import-from-file.component';
import { ExerciseImportComponent } from 'app/exercises/shared/import/exercise-import.component';

let fixture: ComponentFixture<ExerciseImportTabsComponent>;
let comp: ExerciseImportTabsComponent;

let activeModal: NgbActiveModal;
let exerciseImportComponent: ExerciseImportComponent;
let exerciseImportFromFileComponent: ExerciseImportFromFileComponent;
beforeEach(() => {
    TestBed.configureTestingModule({
        imports: [ArtemisTestModule, MockComponent(ExerciseImportFromFileComponent), MockComponent(ExerciseImportComponent)],
        declarations: [ExerciseImportTabsComponent],
    })
        .compileComponents()
        .then(() => {
            fixture = TestBed.createComponent(ExerciseImportTabsComponent);
            comp = fixture.componentInstance;
            activeModal = TestBed.inject(NgbActiveModal);
        });
});

afterEach(() => {
    jest.restoreAllMocks();
});

it('should show first tab when opened', () => {
    // WHEN
    fixture.detectChanges();

    // THEN
    expect(comp.active).toBe(1);
});

it('should close the active modal', () => {
    const dismiss = jest.spyOn(activeModal, 'dismiss');

    // WHEN
    comp.clear();

    // THEN
    expect(dismiss).toHaveBeenCalledOnceWith('cancel');
});
