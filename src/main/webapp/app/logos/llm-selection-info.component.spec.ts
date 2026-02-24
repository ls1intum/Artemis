import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LlmSelectionInfoComponent } from './llm-selection-info.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockDirective } from 'ng-mocks';

describe('LlmSelectionInfoComponent', () => {
    let component: LlmSelectionInfoComponent;
    let fixture: ComponentFixture<LlmSelectionInfoComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LlmSelectionInfoComponent, MockDirective(TranslateDirective)],
        }).compileComponents();

        fixture = TestBed.createComponent(LlmSelectionInfoComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should render the component template', () => {
        const compiled = fixture.nativeElement;
        expect(compiled).toBeTruthy();
    });

    it('should have the correct selector', () => {
        const componentMetadata = (component.constructor as any).__annotations__?.[0];
        expect(componentMetadata?.selector).toBe('jhi-llm-selection-info');
    });
});
