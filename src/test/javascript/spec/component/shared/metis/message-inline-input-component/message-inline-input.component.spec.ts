import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MessageInlineInputComponent } from 'app/shared/metis/message/message-inline-input/message-inline-input.component';
import { MetisService } from 'app/shared/metis/metis.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockModule, MockPipe } from 'ng-mocks';
import { throwError } from 'rxjs';
import { MockMetisService } from '../../../../helpers/mocks/service/mock-metis-service.service';
import { directMessageUser1, metisPostToCreateUser1 } from '../../../../helpers/sample/metis-sample-data';

describe('MessageInlineInputComponent', () => {
    let component: MessageInlineInputComponent;
    let fixture: ComponentFixture<MessageInlineInputComponent>;
    let metisService: MetisService;
    let metisServiceCreateStub: jest.SpyInstance;
    let metisServiceUpdateStub: jest.SpyInstance;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, MockModule(FormsModule), MockModule(ReactiveFormsModule)],
            declarations: [MessageInlineInputComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [FormBuilder, { provide: MetisService, useClass: MockMetisService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MessageInlineInputComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceCreateStub = jest.spyOn(metisService, 'createPost');
                metisServiceUpdateStub = jest.spyOn(metisService, 'updatePost');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should invoke metis service with created post', fakeAsync(() => {
        component.posting = metisPostToCreateUser1;
        component.ngOnChanges();

        const newContent = 'new content';
        const onCreateSpy = jest.spyOn(component.onCreate, 'emit');
        component.formGroup.setValue({
            content: newContent,
        });
        component.confirm();
        expect(metisServiceCreateStub).toHaveBeenCalledWith({
            ...component.posting,
            content: newContent,
            title: undefined,
            courseWideContext: undefined,
            exercise: undefined,
            lecture: undefined,
        });
        tick();
        expect(component.isLoading).toBeFalse();
        expect(onCreateSpy).toHaveBeenCalledOnce();
    }));

    it('should stop loading when metis service throws error during message creation', fakeAsync(() => {
        metisServiceCreateStub.mockImplementation(() => throwError(() => new Error('error')));
        const onCreateSpy = jest.spyOn(component.onCreate, 'emit');

        component.posting = metisPostToCreateUser1;
        component.ngOnChanges();

        const newContent = 'new content';
        component.formGroup.setValue({
            content: newContent,
        });

        component.confirm();

        tick();
        expect(component.isLoading).toBeFalse();
        expect(onCreateSpy).toHaveBeenCalledTimes(0);
    }));

    it('should invoke metis service with edited post', fakeAsync(() => {
        component.posting = directMessageUser1;
        component.ngOnChanges();

        const editedContent = 'edited content';
        const onEditSpy = jest.spyOn(component.isModalOpen, 'emit');

        component.formGroup.setValue({
            content: editedContent,
        });

        component.confirm();

        expect(metisServiceUpdateStub).toHaveBeenCalledWith({
            ...component.posting,
            content: editedContent,
            title: undefined,
            courseWideContext: undefined,
            exercise: undefined,
            lecture: undefined,
        });
        tick();
        expect(component.isLoading).toBeFalse();
        expect(onEditSpy).toHaveBeenCalledOnce();
    }));

    it('should stop loading when metis service throws error during message updating', fakeAsync(() => {
        metisServiceUpdateStub.mockImplementation(() => throwError(() => new Error('error')));

        component.posting = directMessageUser1;
        component.ngOnChanges();

        const editedContent = 'edited content';
        const onEditSpy = jest.spyOn(component.isModalOpen, 'emit');

        component.formGroup.setValue({
            content: editedContent,
        });

        component.confirm();

        tick();
        expect(component.isLoading).toBeFalse();
        expect(onEditSpy).toHaveBeenCalledTimes(0);
    }));
});
