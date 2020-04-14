import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess-new/textblock-feedback-editor/textblock-feedback-editor.component';
import { Feedback } from 'app/entities/feedback.model';
import { TextBlock } from 'app/entities/text-block.model';
import { ArtemisConfirmIconModule } from 'app/shared/confirm-icon/confirm-icon.module';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('TextblockFeedbackEditorComponent', () => {
    let component: TextblockFeedbackEditorComponent;
    let fixture: ComponentFixture<TextblockFeedbackEditorComponent>;
    let compiled: any;

    const textBlock = { id: 'f6773c4b3c2d057fd3ac11f02df31c0a3e75f800' } as TextBlock;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, TranslateModule.forRoot(), ArtemisConfirmIconModule],
            declarations: [TextblockFeedbackEditorComponent],
        })
            .overrideModule(ArtemisTestModule, {
                remove: {
                    declarations: [MockComponent(FaIconComponent)],
                    exports: [MockComponent(FaIconComponent)],
                },
            })
            .compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(TextblockFeedbackEditorComponent);
        component = fixture.componentInstance;
        component.textBlock = textBlock;
        component.feedback = Feedback.forText(textBlock);
        compiled = fixture.debugElement.nativeElement;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();

        const textarea = compiled.querySelector('textarea');
        expect(textarea).toBeTruthy();

        const input = compiled.querySelector('input');
        expect(input).toBeTruthy();
    });

    it('should show delete button for empty feedback only', () => {
        // fixture.debugElement.query(By.css('fa-icon.back-button'));
        let button = compiled.querySelector('.close fa-icon[icon="times"]');
        let confirm = compiled.querySelector('.close jhi-confirm-icon');
        expect(button).toBeTruthy();
        expect(confirm).toBeFalsy();

        component.feedback.credits = 1;
        fixture.detectChanges();
        button = compiled.querySelector('.close fa-icon[icon="times"]');
        confirm = compiled.querySelector('.close jhi-confirm-icon');
        expect(button).toBeFalsy();
        expect(confirm).toBeTruthy();

        component.feedback.detailText = 'Lorem Ipsum';
        fixture.detectChanges();
        button = compiled.querySelector('.close fa-icon[icon="times"]');
        confirm = compiled.querySelector('.close jhi-confirm-icon');
        expect(button).toBeFalsy();
        expect(confirm).toBeTruthy();

        component.feedback.credits = 0;
        fixture.detectChanges();
        button = compiled.querySelector('.close fa-icon[icon="times"]');
        confirm = compiled.querySelector('.close jhi-confirm-icon');
        expect(button).toBeFalsy();
        expect(confirm).toBeTruthy();

        component.feedback.detailText = '';
        fixture.detectChanges();

        button = compiled.querySelector('.close fa-icon[icon="times"]');
        confirm = compiled.querySelector('.close jhi-confirm-icon');
        expect(button).toBeTruthy();
        expect(confirm).toBeFalsy();
    });
});
