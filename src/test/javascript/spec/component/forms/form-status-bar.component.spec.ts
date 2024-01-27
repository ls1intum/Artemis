import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { FormStatusBarComponent } from 'app/forms/form-status-bar/form-status-bar.component';

describe('FormStatusBarComponent', () => {
    let fixture: ComponentFixture<FormStatusBarComponent>;
    let comp: FormStatusBarComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FormStatusBarComponent);
                comp = fixture.componentInstance;
            });
    });

    beforeEach(() => {
        comp.formStatusSections = [
            { title: 'some-translation-key', valid: true },
            { title: 'another-translation-key', valid: false },
        ];
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initializes', () => {
        expect(comp).toBeDefined();
    });

    it('should scroll to correct headline', () => {
        const scrollSpy = jest.spyOn(document, 'getElementById');
        comp.scrollToHeadline(comp.formStatusSections[0].title);
        expect(scrollSpy).toHaveBeenCalledWith(comp.formStatusSections[0].title);
    });
});
