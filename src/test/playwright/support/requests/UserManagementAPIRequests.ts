import { UserRole } from '../users';
import { BASE_API } from '../constants';
import { Page } from '@playwright/test';
import { APIResponse } from 'playwright-core';

/**
 * A class which encapsulates all API requests related to user management.
 */
export class UserManagementAPIRequests {
    private page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Creates a new user
     * @param username the username of the new user
     * @param password the password of the new user
     * @param role the role of the new user
     */

    fullUrl = new URL(`${process.env.baseURL}/${BASE_API}public/authenticate`);

    async createUser(username: string, password: string, role: UserRole): Promise<APIResponse> {
        // const data = JSON.stringify({
        //     login: username,
        //     password,
        //     firstName: username,
        //     lastName: username,
        //     email: username + '@example.com',
        //     authorities: [role],
        // });

        // const options = {
        //     hostname: this.fullUrl.hostname,
        //     port: this.fullUrl.port.length != 0 ? this.fullUrl.port : null,
        //     path: `/${BASE_API}admin/users`,
        //     method: 'POST',
        //     // agent: new https.Agent({
        //     //     ca: fs.readFileSync('./certs/rootCA.pem'),
        //     //     cert: fs.readFileSync('./certs/artemis-nginx+4.pem'),
        //     //     key: fs.readFileSync('./certs/artemis-nginx+4-key.pem'),
        //     // }),
        //     headers: {
        //         'Content-Type': 'application/json',
        //     },
        // };

        // console.log(options);
        return await this.page.request.post(`${BASE_API}admin/users`, {
            // certificateOptions: {
            //     ca: fs.readFileSync('./certs/rootCA.pem'),
            //     cert: fs.readFileSync('./certs/artemis-nginx+4.pem'),
            //     key: fs.readFileSync('./certs/artemis-nginx+4-key.pem'),
            // },
            data: {
                login: username,
                password,
                firstName: username,
                lastName: username,
                email: username + '@example.com',
                authorities: [role],
            },
            ignoreHTTPSErrors: true,
        });
        // const req = http.request(options, (res) => {
        //     console.log(`statusCode: ${res.statusCode}`);
        //
        //     res.on('data', (data) => {
        //         process.stdout.write(data);
        //         return data;
        //     });
        // });
        //
        // req.on('error', (error) => {
        //     console.error(error);
        // });
        //
        // req.write(data);
        // req.end();
    }

    async getUser(username: string): Promise<APIResponse> {
        // const options = {
        //     hostname: this.fullUrl.hostname,
        //     port: this.fullUrl.port.length != 0 ? this.fullUrl.port : null,
        //     path: `/${BASE_API}users/${username}`,
        //     method: 'GET',
        //     // agent: new https.Agent({
        //     //     ca: fs.readFileSync('./certs/rootCA.pem'),
        //     //     cert: fs.readFileSync('./certs/artemis-nginx+4.pem'),
        //     //     key: fs.readFileSync('./certs/artemis-nginx+4-key.pem'),
        //     // }),
        // };
        return await this.page.request.get(`${BASE_API}users/${username}`, {
            // certificateOptions: {
            //     ca: fs.readFileSync('./certs/rootCA.pem'),
            //     cert: fs.readFileSync('./certs/artemis-nginx+4.pem'),
            //     key: fs.readFileSync('./certs/artemis-nginx+4-key.pem'),
            // },
            ignoreHTTPSErrors: true,
        });
        // return new Promise((resolve) => {
        //     console.log(options);
        //     const req = http.request(options, (res) => {
        //         console.log(`statusCode: ${res.statusCode}`);
        //
        //         res.on('data', (data) => {
        //             process.stdout.write(data);
        //         });
        //
        //         res.on('end', () => {
        //             resolve(res.statusCode);
        //         });
        //     });
        //
        //     req.on('error', (error) => {
        //         console.error(error);
        //     });
        //
        //     req.end();
        // });
    }
}
