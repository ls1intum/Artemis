import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { BuildPhasesTemplateService } from './build-phases-template.service';
import { ProgrammingLanguage, ProjectType } from '../entities/programming-exercise.model';

describe('BuildPhasesTemplateService', () => {
    setupTestBed({ zoneless: true });

    let service: BuildPhasesTemplateService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(BuildPhasesTemplateService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('builds the language/project endpoint and passes boolean query params', () => {
        service.getTemplate(ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN, true, false).subscribe();

        const req = httpMock.expectOne((request) => request.method === 'GET' && request.url === 'api/programming/phases/templates/JAVA/PLAIN_MAVEN');
        expect(req.request.params.get('staticAnalysis')).toBe('true');
        expect(req.request.params.get('sequentialRuns')).toBe('false');
        req.flush({ phases: [] });
    });

    it('omits project type path segment and defaults booleans to false', () => {
        service.getTemplate(ProgrammingLanguage.KOTLIN).subscribe();

        const req = httpMock.expectOne((request) => request.method === 'GET' && request.url === 'api/programming/phases/templates/KOTLIN');
        expect(req.request.params.get('staticAnalysis')).toBe('false');
        expect(req.request.params.get('sequentialRuns')).toBe('false');
        req.flush({ phases: [] });
    });
});
