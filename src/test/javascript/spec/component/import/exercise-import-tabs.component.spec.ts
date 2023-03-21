import { ArtemisTestModule } from '../../test.module';
import { ExerciseCourseTitlePipe } from 'app/shared/pipes/exercise-course-title.pipe';
import { ButtonComponent } from 'app/shared/components/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExerciseImportTabsComponent } from 'app/exercises/shared/import/exercise-import-tabs.component';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal, NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { FormsModule } from '@angular/forms';
import { ExerciseImportFromFileComponent } from 'app/exercises/shared/import/from-file/exercise-import-from-file.component';
import { ExerciseImportComponent } from 'app/exercises/shared/import/exercise-import.component';
import { NgbPaginationNextMockDirective } from '../../helpers/mocks/directive/ngbPaginationMocks.module';
import { By } from '@angular/platform-browser';

let fixture: ComponentFixture<ExerciseImportTabsComponent>;
let comp: ExerciseImportTabsComponent;

beforeEach(() => {
    TestBed.configureTestingModule({
        imports: [ArtemisTestModule, NgbNavModule, MockComponent(ExerciseImportFromFileComponent), MockComponent(ExerciseImportComponent), FormsModule],
        declarations: [ExerciseImportTabsComponent, MockDirective(TranslateDirective)],
    })
        .compileComponents()
        .then(() => {
            fixture = TestBed.createComponent(ExerciseImportTabsComponent);
            comp = fixture.componentInstance;
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
