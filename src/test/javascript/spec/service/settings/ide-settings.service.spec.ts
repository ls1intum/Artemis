import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { IdeSettingsService } from 'app/shared/user-settings/ide-preferences/ide-settings.service';
import { Ide, IdeMappingDTO } from 'app/shared/user-settings/ide-preferences/ide.model';
import { ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';
import { provideHttpClient } from '@angular/common/http';

describe('IdeSettingsService', () => {
    let service: IdeSettingsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            providers: [provideHttpClient(), provideHttpClientTesting(), IdeSettingsService],
        });
        service = TestBed.inject(IdeSettingsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should load predefined IDEs', () => {
        const mockIdes: Ide[] = [{ name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' }];

        service.loadPredefinedIdes().subscribe((ides) => {
            expect(ides).toEqual(mockIdes);
        });

        const req = httpMock.expectOne(service.ideSettingsUrl + '/predefined');
        expect(req.request.method).toBe('GET');
        req.flush(mockIdes);
    });

    it('should load IDE preferences', fakeAsync(() => {
        const mockIdeMappingDTO: IdeMappingDTO[] = [
            { programmingLanguage: ProgrammingLanguage.JAVA, ide: { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' } },
        ];
        const expectedMap = new Map<ProgrammingLanguage, Ide>([[ProgrammingLanguage.JAVA, { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' }]]);

        service.loadIdePreferences().then((ideMap) => {
            expect(ideMap).toEqual(expectedMap);
        });

        tick();

        const req = httpMock.expectOne(service.ideSettingsUrl);
        expect(req.request.method).toBe('GET');
        req.flush(mockIdeMappingDTO);
    }));

    it('should save IDE preference', () => {
        const programmingLanguage = ProgrammingLanguage.JAVA;
        const ide: Ide = { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' };
        const mockResponse: IdeMappingDTO = { programmingLanguage, ide };

        service.saveIdePreference(programmingLanguage, ide).subscribe((savedIde) => {
            expect(savedIde).toEqual(ide);
        });

        const req = httpMock.expectOne((request) => request.url === service.ideSettingsUrl && request.params.get('programmingLanguage') === programmingLanguage);
        expect(req.request.method).toBe('PUT');
        req.flush(mockResponse);
    });

    it('should delete IDE preference', () => {
        const programmingLanguage = ProgrammingLanguage.JAVA;

        service.deleteIdePreference(programmingLanguage).subscribe((response) => {
            expect(response.status).toBe(200);
        });

        const req = httpMock.expectOne((request) => request.url === service.ideSettingsUrl && request.params.get('programmingLanguage') === programmingLanguage);
        expect(req.request.method).toBe('DELETE');
        req.flush(null, { status: 200, statusText: 'OK' });
    });
});
