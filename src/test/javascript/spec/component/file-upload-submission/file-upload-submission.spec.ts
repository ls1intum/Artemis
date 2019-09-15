import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { AccountService, JhiLanguageHelper } from 'app/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { MockParticipationWebsocketService, MockSyncStorage } from '../../mocks';
import { ArtemisResultModule } from 'app/entities/result';
import { MockComponent } from 'ng-mocks';
import { ArtemisSharedModule } from 'app/shared';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { JhiAlertService } from 'ng-jhipster';
import { Router } from '@angular/router';
import { ResizableInstructionsComponent } from 'app/text-assessment/resizable-instructions/resizable-instructions.component';
import { ComplaintsForTutorComponent } from 'app/complaints-for-tutor';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockAccountService } from '../../mocks/mock-account.service';
import { Location } from '@angular/common';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { MockComplaintService } from '../../mocks/mock-complaint.service';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { fileUploadSubmissionRoute } from 'app/file-upload-submission/file-upload-submission.route';
import { FileUploadSubmissionComponent } from 'app/file-upload-submission/file-upload-submission.component';
import { MomentModule } from 'ngx-moment';
import { ArtemisComplaintsModule } from 'app/complaints';
import { FileUploadSubmissionService } from 'app/entities/file-upload-submission';
import { MockFileUploadSubmissionService } from '../../mocks/mock-file-upload-submission.service';
import { ParticipationWebsocketService } from 'app/entities/participation';
import { fileUploadExercise } from '../../mocks/mock-file-upload-exercise.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('FileUploadSubmissionComponent', () => {
    let comp: FileUploadSubmissionComponent;
    let fixture: ComponentFixture<FileUploadSubmissionComponent>;
    let debugElement: DebugElement;
    let router: Router;
    let location: Location;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                NgxDatatableModule,
                ArtemisResultModule,
                ArtemisSharedModule,
                MomentModule,
                ArtemisComplaintsModule,
                RouterTestingModule.withRoutes([fileUploadSubmissionRoute[0]]),
            ],
            declarations: [FileUploadSubmissionComponent, MockComponent(ResizableInstructionsComponent), MockComponent(ComplaintsForTutorComponent)],
            providers: [
                JhiLanguageHelper,
                { provide: JhiAlertService, useClass: MockAlertService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: ComplaintService, useClass: MockComplaintService },
                { provide: FileUploadSubmissionService, useClass: MockFileUploadSubmissionService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadSubmissionComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                router = debugElement.injector.get(Router);
                location = debugElement.injector.get(Location);
                router.initialNavigation();
            });
    });

    it('File Upload Submission is correctly initialized from service', fakeAsync(() => {
        fixture.detectChanges();
        comp.ngOnInit();
        tick();

        expect(comp.acceptedFileExtensions.replace(/\./g, '')).to.be.equal(fileUploadExercise.filePattern);

        // check if fileUploadInput is available
        const fileUploadInput = debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).to.exist;

        // check if fileUploadLabel value is not set
        const fileUploadLabel = debugElement.query(By.css('.custom-file-label.overflow-ellipsis'));
        expect(fileUploadLabel).to.exist;
        expect(fileUploadLabel.nativeElement.value).to.be.undefined;

        // check if extension elements are set
        const extension = debugElement.query(By.css('.ml-1.badge.badge-info'));
        expect(extension).to.exist;
        expect(extension.nativeElement.textContent.replace(/\s/g, '')).to.be.equal(fileUploadExercise.filePattern.split(',')[0].toUpperCase());

        fixture.destroy();
        flush();
    }));
});
