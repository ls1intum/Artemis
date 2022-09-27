import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialGroupsRegistrationImportDialog } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-groups-import-dialog/tutorial-groups-registration-import-dialog.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('TutorialGroupsImportDialogComponent', () => {
    let component: TutorialGroupsRegistrationImportDialog;
    let fixture: ComponentFixture<TutorialGroupsRegistrationImportDialog>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FormsModule, ReactiveFormsModule],
            declarations: [TutorialGroupsRegistrationImportDialog, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent)],
            providers: [MockProvider(TranslateService), MockProvider(AlertService), MockProvider(TutorialGroupsService), MockProvider(NgbActiveModal)],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupsRegistrationImportDialog);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
