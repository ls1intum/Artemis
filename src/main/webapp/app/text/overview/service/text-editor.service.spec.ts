/**
 * Test suite for TextEditorService.
 * Tests language prediction functionality for German and English text.
 */
import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TextEditorService } from 'app/text/overview/service/text-editor.service';

import { provideHttpClient } from '@angular/common/http';
import { Language } from 'app/core/course/shared/entities/course.model';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';

describe('TextEditorService', () => {
    setupTestBed({ zoneless: true });
    let textEditorService: TextEditorService;
    let httpMock: HttpTestingController;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        textEditorService = TestBed.inject(TextEditorService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });
    it('Can detect a short German string', () => {
        const testString = 'Das ist ein kurzer, deutscher Satz';
        expect(textEditorService.predictLanguage(testString)).toBe(Language.GERMAN);
    });
    it("Can detect that a French sentence isn't German or English", () => {
        const testString = "Il s'agit d'une courte phrase en français";
        expect(textEditorService.predictLanguage(testString)).toBeUndefined();
    });
    it('Can detect a short English string', () => {
        const testString = 'This is an English String';
        expect(textEditorService.predictLanguage(testString)).toBe(Language.ENGLISH);
    });
    it('Can detect a long German text', () => {
        // copied from https://de.wikipedia.org/wiki/Bernd_Br%C3%BCgge

        const testString =
            'Bernd Brügge ist ein deutscher Informatiker und Ordinarius für Angewandte Softwaretechnik' +
            ' an der Fakultät für Informatik der Technischen Universität München. ' +
            'Er hält zudem eine außerordentliche Professur an der Carnegie Mellon University in Pittsburgh.\n' +
            'Brügge beschäftigt sich insbesondere mit Software-Architekturen dynamischer Systeme, ' +
            'agilen Software-Entwicklungsprozessen und der Lehre vom Software Engineering. ' +
            'Er promovierte an der Carnegie Mellon University (Ph.D.).';
        expect(textEditorService.predictLanguage(testString)).toBe(Language.GERMAN);
    });
    it('Can detect a long English text', () => {
        // copied from https://en.wikipedia.org/wiki/Bernd_Bruegge

        const testString =
            'Bernd Bruegge (German: Bernd Brügge) (born 1951) is a German computer scientist,' +
            ' full professor at the Technische Universität München (TUM)' +
            ' and the head of the Chair for Applied Software Engineering.' +
            '[1] He is also an adjunct associate professor at Carnegie Mellon University (CMU) in Pittsburgh. ' +
            "Born in 1951, Bruegge received a bachelor's degree in computer science at the University of Hamburg" +
            " in 1978, a master's degree in computer science from Carnegie Mellon University in 1982 " +
            'and a PhD degree in computer science from Carnegie Mellon University in 1985.';
        expect(textEditorService.predictLanguage(testString)).toBe(Language.ENGLISH);
    });

    it('should get participation without resultId parameter', () => {
        const participationId = 123;
        const mockParticipation = { id: participationId } as StudentParticipation;

        textEditorService.get(participationId).subscribe((participation) => {
            expect(participation.id).toBe(participationId);
        });

        const req = httpMock.expectOne('api/text/text-editor/123');
        expect(req.request.method).toBe('GET');
        expect(req.request.params.keys()).toHaveLength(0);
        req.flush(mockParticipation);
    });

    it('should get participation with resultId parameter', () => {
        const participationId = 123;
        const resultId = 456;
        const mockParticipation = {
            id: participationId,
            submissions: [{ results: [{ id: resultId }] }],
        } as StudentParticipation;

        textEditorService.get(participationId, resultId).subscribe((participation) => {
            expect(participation.id).toBe(participationId);
        });

        const req = httpMock.expectOne(`api/text/text-editor/123?resultId=${resultId}`);
        expect(req.request.method).toBe('GET');
        expect(req.request.params.get('resultId')).toBe(resultId.toString());
        req.flush(mockParticipation);
    });
});
