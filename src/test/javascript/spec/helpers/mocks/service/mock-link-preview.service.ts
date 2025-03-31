import { Observable, of } from 'rxjs';
import { LinkPreview } from 'app/communication/link-preview/services/link-preview.service';

export class MockLinkPreviewService {
    fetchLink(url: string): Observable<LinkPreview> {
        return of({} as LinkPreview);
    }
}
