import { ArtemisTestModule } from '../../test.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExerciseImportTabsComponent } from 'app/exercises/shared/import/exercise-import-tabs.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective } from 'ng-mocks';
import { FormsModule } from '@angular/forms';
import { ExerciseImportFromFileComponent } from 'app/exercises/shared/import/from-file/exercise-import-from-file.component';
import { ExerciseImportComponent } from 'app/exercises/shared/import/exercise-import.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';

describe('ExerciseImportTabsComponent', () => {
    let fixture: ComponentFixture<ExerciseImportTabsComponent>;
    let comp: ExerciseImportTabsComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                NgbNavModule,
                MockComponent(ExerciseImportFromFileComponent),
                MockComponent(HelpIconComponent),
                MockComponent(ExerciseImportComponent),
                FormsModule,
            ],
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
