import { HttpRequest } from '@angular/common/http';

/**
 * Tests if given Request is sent to Artemis Server
 * @param request Request to test
 */
export const isRequestToArtemisServer = (request: HttpRequest<any>): boolean =>
    !(!request || !request.url || (/^http/.test(request.url) && !(SERVER_API_URL && request.url.startsWith(SERVER_API_URL))));
