import { Component, EventEmitter, Input, Output } from '@angular/core';
import { VideoUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/video-unit-form/video-unit-form.component';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { VideoUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/videoUnit.service';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import dayjs from 'dayjs/esm';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { LectureUpdateWizardUnitsComponent } from 'app/lecture/wizard-mode/lecture-wizard-units.component';
import { Lecture } from 'app/entities/lecture.model';
import { TextUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/textUnit.service';
import { OnlineUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/onlineUnit.service';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { LectureUnitManagementComponent } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.component';

@Component({ selector: 'jhi-video-unit-form', template: '' })
class VideoUnitFormStubComponent {
    @Input() isEditMode = false;
    @Output() formSubmitted: EventEmitter<VideoUnitFormData> = new EventEmitter<VideoUnitFormData>();
}

@Component({ selector: 'jhi-unit-creation-card', template: '' })
class UnitCreationCardStubComponent {
    @Input() emitEvents = true;
    @Output() onUnitCreationCardClicked: EventEmitter<LectureUnitType> = new EventEmitter<LectureUnitType>();
}

describe('LectureWizardUnitComponent', () => {
    let wizardUnitComponentFixture: ComponentFixture<LectureUpdateWizardUnitsComponent>;
    let wizardUnitComponent: LectureUpdateWizardUnitsComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [VideoUnitFormStubComponent, UnitCreationCardStubComponent, LectureUpdateWizardUnitsComponent],
            providers: [
                MockProvider(VideoUnitService),
                MockProvider(AlertService),
                MockProvider(TextUnitService),
                MockProvider(OnlineUnitService),
                MockProvider(AttachmentUnitService),
                MockProvider(LectureUnitManagementComponent),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: { queryParams: of({}) },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                wizardUnitComponentFixture = TestBed.createComponent(LectureUpdateWizardUnitsComponent);
                wizardUnitComponent = wizardUnitComponentFixture.componentInstance;
                wizardUnitComponent.lecture = new Lecture();
                wizardUnitComponent.lecture.id = 1;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        wizardUnitComponentFixture.detectChanges();
        expect(wizardUnitComponent).not.toBeNull();
    });

    it('should open video form when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();
        const unitCreationCard: UnitCreationCardStubComponent = wizardUnitComponentFixture.debugElement.query(By.directive(UnitCreationCardStubComponent)).componentInstance;
        unitCreationCard.onUnitCreationCardClicked.emit(LectureUnitType.VIDEO);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(wizardUnitComponent.isVideoUnitFormOpen).toBe(true);
        });
    }));

    it('should open online form when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();
        const unitCreationCard: UnitCreationCardStubComponent = wizardUnitComponentFixture.debugElement.query(By.directive(UnitCreationCardStubComponent)).componentInstance;
        unitCreationCard.onUnitCreationCardClicked.emit(LectureUnitType.ONLINE);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(wizardUnitComponent.isOnlineUnitFormOpen).toBe(true);
        });
    }));

    it('should open attachment form when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();
        const unitCreationCard: UnitCreationCardStubComponent = wizardUnitComponentFixture.debugElement.query(By.directive(UnitCreationCardStubComponent)).componentInstance;
        unitCreationCard.onUnitCreationCardClicked.emit(LectureUnitType.ATTACHMENT);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(wizardUnitComponent.isAttachmentUnitFormOpen).toBe(true);
        });
    }));

    it('should open text form when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();
        const unitCreationCard: UnitCreationCardStubComponent = wizardUnitComponentFixture.debugElement.query(By.directive(UnitCreationCardStubComponent)).componentInstance;
        unitCreationCard.onUnitCreationCardClicked.emit(LectureUnitType.TEXT);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(wizardUnitComponent.isTextUnitFormOpen).toBe(true);
        });
    }));

    it('should open exercise form when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();
        const unitCreationCard: UnitCreationCardStubComponent = wizardUnitComponentFixture.debugElement.query(By.directive(UnitCreationCardStubComponent)).componentInstance;
        unitCreationCard.onUnitCreationCardClicked.emit(LectureUnitType.EXERCISE);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(wizardUnitComponent.isExerciseUnitFormOpen).toBe(true);
        });
    }));

    it('should close all forms when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();
        wizardUnitComponent.onCloseLectureUnitForms();

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(wizardUnitComponent.isOnlineUnitFormOpen).toBe(false);
            expect(wizardUnitComponent.isTextUnitFormOpen).toBe(false);
            expect(wizardUnitComponent.isExerciseUnitFormOpen).toBe(false);
            expect(wizardUnitComponent.isAttachmentUnitFormOpen).toBe(false);
            expect(wizardUnitComponent.isVideoUnitFormOpen).toBe(false);
        });
    }));

    it('should send POST request upon video form submission and update units', fakeAsync(() => {
        const videoUnitService = TestBed.inject(VideoUnitService);

        const formData: VideoUnitFormData = {
            name: 'Test',
            releaseDate: dayjs().year(2010).month(3).date(5),
            description: 'Lorem Ipsum',
            source: 'https://www.youtube.com/embed/8iU8LPEa4o0',
        };

        const response: HttpResponse<VideoUnit> = new HttpResponse({
            body: new VideoUnit(),
            status: 201,
        });

        const createStub = jest.spyOn(videoUnitService, 'create').mockReturnValue(of(response));

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);

        const updateSpy = jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.isVideoUnitFormOpen = true;

        wizardUnitComponent.createEditVideoUnit(formData);

        wizardUnitComponentFixture.whenStable().then(() => {
            const videoUnitCallArgument: VideoUnit = createStub.mock.calls[0][0];
            const lectureIdCallArgument: number = createStub.mock.calls[0][1];

            expect(videoUnitCallArgument.name).toEqual(formData.name);
            expect(videoUnitCallArgument.description).toEqual(formData.description);
            expect(videoUnitCallArgument.releaseDate).toEqual(formData.releaseDate);
            expect(videoUnitCallArgument.source).toEqual(formData.source);
            expect(lectureIdCallArgument).toBe(1);

            expect(createStub).toHaveBeenCalledOnce();
            expect(updateSpy).toHaveBeenCalledOnce();

            updateSpy.mockRestore();
        });
    }));
});
