import { CypressUserManagement } from './users';
import { ArtemisPageobjects } from './pageobjects/ArtemisPageobjects';
import { ArtemisRequests } from './requests/ArtemisRequests';

/**
 * Class which contains all shared code related to testing Artemis.
 */
export class ArtemisTesting {
    /**
     * All requests which might have to be sent directly to the Artemis server.
     */
    public requests = new ArtemisRequests();

    /**
     * Pageobjects, which contain code to automate certain pages in Artemis, which are used in multiple tests.
     */
    public pageobjects = new ArtemisPageobjects();

    /**
     * Can be used to retrieve test users.
     */
    public users = new CypressUserManagement();
}
export const artemis = new ArtemisTesting();
