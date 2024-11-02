import { TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseBuildConfigurationComponent } from 'app/exercises/programming/manage/update/update-components/custom-build-plans/programming-exercise-build-configuration/programming-exercise-build-configuration.component';
import { FormsModule } from '@angular/forms';
import { ArtemisProgrammingExerciseUpdateModule } from 'app/exercises/programming/manage/update/programming-exercise-update.module';
import { of } from 'rxjs';
import { ProfileService } from '../../../../../main/webapp/app/shared/layouts/profiles/profile.service';

describe('ProgrammingExercise Docker Image', () => {
    let comp: ProgrammingExerciseBuildConfigurationComponent;
    let profileServiceMock: { getProfileInfo: jest.Mock };

    beforeEach(() => {
        profileServiceMock = {
            getProfileInfo: jest.fn(),
        };

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, ArtemisProgrammingExerciseUpdateModule],
            declarations: [ProgrammingExerciseBuildConfigurationComponent],
            providers: [{ provide: ProfileService, useValue: profileServiceMock }],
        })
            .compileComponents()
            .then();

        const fixture = TestBed.createComponent(ProgrammingExerciseBuildConfigurationComponent);
        comp = fixture.componentInstance;

        fixture.componentRef.setInput('dockerImage', 'testImage');
        fixture.componentRef.setInput('timeout', 10);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should update build values', () => {
        expect(comp.dockerImage()).toBe('testImage');
        comp.dockerImageChange.subscribe((value) => expect(value).toBe('newImage'));
        comp.dockerImageChange.emit('newImage');

        expect(comp.timeout()).toBe(10);
        comp.timeoutChange.subscribe((value) => expect(value).toBe(20));
        comp.timeoutChange.emit(20);
    });

    it('should set timeout options', () => {
        profileServiceMock.getProfileInfo.mockReturnValue(
            of({
                buildTimeoutMin: undefined,
                buildTimeoutMax: undefined,
                buildTimeoutDefault: undefined,
            }),
        );

        comp.ngOnInit();
        expect(comp.timeoutMinValue).toBe(10);
        expect(comp.timeoutMaxValue).toBe(240);
        expect(comp.timeoutDefaultValue).toBe(120);

        profileServiceMock.getProfileInfo.mockReturnValue(
            of({
                buildTimeoutMin: 0,
                buildTimeoutMax: 360,
                buildTimeoutDefault: 60,
            }),
        );
        comp.ngOnInit();
        expect(comp.timeoutMinValue).toBe(0);
        expect(comp.timeoutMaxValue).toBe(360);
        expect(comp.timeoutDefaultValue).toBe(60);

        profileServiceMock.getProfileInfo.mockReturnValue(
            of({
                buildTimeoutMin: 100,
                buildTimeoutMax: 20,
                buildTimeoutDefault: 10,
            }),
        );

        comp.ngOnInit();
        expect(comp.timeoutMinValue).toBe(100);
        expect(comp.timeoutMaxValue).toBe(240);
        expect(comp.timeoutDefaultValue).toBe(120);
    });
});
