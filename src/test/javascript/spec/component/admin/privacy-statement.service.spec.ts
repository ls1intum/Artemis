import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PrivacyStatementService } from 'app/admin/privacy-statement/privacy-statement.service';

describe('PrivacyStatementService', () => {
    let service: PrivacyStatementService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        service = TestBed.inject(PrivacyStatementService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
