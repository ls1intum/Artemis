import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExerciseImportTabsComponent } from 'app/exercise/import/exercise-import-tabs/exercise-import-tabs.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective } from 'ng-mocks';
import { FormsModule } from '@angular/forms';
import { ExerciseImportFromFileComponent } from 'app/exercise/import/from-file/exercise-import-from-file.component';
import { ExerciseImportComponent } from 'app/exercise/import/exercise-import.component';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';

describe('ExerciseImportTabsComponent', () => {
    let fixture: ComponentFixture<ExerciseImportTabsComponent>;
    let comp: ExerciseImportTabsComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgbNavModule, MockComponent(ExerciseImportFromFileComponent), MockComponent(HelpIconComponent), MockComponent(ExerciseImportComponent), FormsModule],
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
        expect(comp.activeTab).toBe(1);
    });
});
