import { Link } from 'app/shared/link-preview/services/linkify.service';

export class MockLinkifyService {
    find(text: string): Link[] {
        return [];
    }
}
