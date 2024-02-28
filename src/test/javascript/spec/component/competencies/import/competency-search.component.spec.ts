import { ArtemisTestModule } from '../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { CompetencySearchComponent } from 'app/course/competencies/import-competencies/competency-search.component';
import { NgbCollapseMocksModule } from '../../../helpers/mocks/directive/ngbCollapseMocks.module';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormsModule } from 'app/forms/forms.module';

describe('CompetencySearchComponent', () => {
    let componentFixture: ComponentFixture<CompetencySearchComponent>;
    let component: CompetencySearchComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, NgbCollapseMocksModule],
            declarations: [CompetencySearchComponent, MockPipe(ArtemisTranslatePipe), ButtonComponent],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CompetencySearchComponent);
                component = componentFixture.componentInstance;
                component.search = {
                    title: '',
                    semester: '',
                    courseTitle: '',
                    description: '',
                };
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should reset', () => {
        componentFixture.detectChanges();
        for (const key in component.search) {
            component.search[key] = 'any value';
        }

        componentFixture.debugElement.nativeElement.querySelector('#resetFilterButton > .jhi-btn').click();

        for (const key in component.search) {
            expect(component.search[key]).toBe('');
        }
    });

    it('should submit with only title', () => {
        componentFixture.detectChanges();
        const searchChangeEmitSpy = jest.spyOn(component.searchChange, 'emit');

        for (const key in component.search) {
            component.search[key] = 'any value';
        }

        componentFixture.debugElement.nativeElement.querySelector('#submitFilterButton > .jhi-btn').click();
        expect(searchChangeEmitSpy).toHaveBeenCalledWith({ title: 'any value', description: '', courseTitle: '', semester: '' });
    });

    it('should submit with advanced search', () => {
        componentFixture.detectChanges();
        const searchChangeEmitSpy = jest.spyOn(component.searchChange, 'emit');

        for (const key in component.search) {
            component.search[key] = 'any value';
        }
        component.advancedSearchEnabled = true;

        componentFixture.debugElement.nativeElement.querySelector('#submitFilterButton > .jhi-btn').click();
        expect(searchChangeEmitSpy).toHaveBeenCalledWith({ title: 'any value', description: 'any value', courseTitle: 'any value', semester: 'any value' });
    });

    it('should toggle advanced search', () => {
        componentFixture.detectChanges();
        const advancedSearchToggle = componentFixture.debugElement.nativeElement.querySelector('#toggleAdvancedSearch');

        advancedSearchToggle.click();
        expect(component.advancedSearchEnabled).toBeTrue();

        advancedSearchToggle.click();
        expect(component.advancedSearchEnabled).toBeFalse();
    });
});
