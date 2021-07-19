import { TestBed, ComponentFixture } from '@angular/core/testing';
import { TextAssessmentAreaComponent } from 'app/exercises/text/assess/text-assessment-area/text-assessment-area.component';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TextblockAssessmentCardComponent } from 'app/exercises/text/assess/textblock-assessment-card/textblock-assessment-card.component';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess/textblock-feedback-editor/textblock-feedback-editor.component';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { By } from '@angular/platform-browser';
import { ArtemisConfirmIconModule } from 'app/shared/confirm-icon/confirm-icon.module';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ManualTextblockSelectionComponent } from 'app/exercises/text/assess/manual-textblock-selection/manual-textblock-selection.component';
import { TextSharedModule } from 'app/exercises/text/shared/text-shared.module';
import { AssessmentCorrectionRoundBadgeComponent } from 'app/assessment/assessment-detail/assessment-correction-round-badge/assessment-correction-round-badge.component';
import { ArtemisGradingInstructionLinkIconModule } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';

describe('TextAssessmentAreaComponent', () => {
    let component: TextAssessmentAreaComponent;
    let fixture: ComponentFixture<TextAssessmentAreaComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, ArtemisConfirmIconModule, TextSharedModule, ArtemisGradingInstructionLinkIconModule],
            declarations: [
                TextAssessmentAreaComponent,
                TextblockAssessmentCardComponent,
                TextblockFeedbackEditorComponent,
                ManualTextblockSelectionComponent,
                AssessmentCorrectionRoundBadgeComponent,
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
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
        fixture = TestBed.createComponent(TextAssessmentAreaComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should add a TextblockAssessmentCardComponent for each TextBlockRef', () => {
        const textBlockRefs = [
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
        ];

        for (let i = 0; i < textBlockRefs.length; i++) {
            component.textBlockRefs = textBlockRefs.slice(0, i);
            fixture.detectChanges();

            const all = fixture.debugElement.queryAll(By.directive(TextblockAssessmentCardComponent));
            expect(all.length).toBe(i);
        }
    });
});
