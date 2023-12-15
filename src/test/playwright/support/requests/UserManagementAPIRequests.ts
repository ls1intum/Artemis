import { UserRole } from '../users';
import * as https from 'https';
import { BASE_API } from '../../../cypress/support/constants';
import fs from 'fs';

/**
 * A class which encapsulates all API requests related to user management.
 */
export class UserManagementAPIRequests {
    /**
     * Creates a new user
     * @param username the username of the new user
     * @param password the password of the new user
     * @param role the role of the new user
     */

    fullUrl = new URL(`${process.env.baseURL}/${BASE_API}public/authenticate`);

    createUser(username: string, password: string, role: UserRole) {
        const data = JSON.stringify({
            login: username,
            password,
            firstName: username,
            lastName: username,
            email: username + '@example.com',
            authorities: [role],
        });

        const options = {
            hostname: this.fullUrl.hostname,
            port: this.fullUrl.port.length != 0 ? this.fullUrl.port : null,
            path: '/admin/users',
            method: 'POST',
            agent: new https.Agent({
                ca: fs.readFileSync('./certs/rootCA.pem'),
                cert: fs.readFileSync('./certs/artemis-nginx+4.pem'),
                key: fs.readFileSync('./certs/artemis-nginx+4-key.pem'),
            }),
            headers: {
                'Content-Type': 'application/json',
            },
        };

        const req = https.request(options, (res) => {
            console.log(`statusCode: ${res.statusCode}`);

            res.on('data', (data) => {
                process.stdout.write(data);
                return data;
            });
        });

        req.on('error', (error) => {
            console.error(error);
        });

        req.write(data);
        req.end();
    }

    getUser(username: string): Promise<number | undefined> {
        const options = {
            hostname: this.fullUrl.hostname,
            port: this.fullUrl.port.length != 0 ? this.fullUrl.port : null,
            path: `/users/${username}`,
            method: 'GET',
            agent: new https.Agent({
                ca: fs.readFileSync('./certs/rootCA.pem'),
                cert: fs.readFileSync('./certs/artemis-nginx+4.pem'),
                key: fs.readFileSync('./certs/artemis-nginx+4-key.pem'),
            }),
        };

        return new Promise((resolve) => {
            const req = https.request(options, (res) => {
                console.log(`statusCode: ${res.statusCode}`);

                res.on('data', (data) => {
                    process.stdout.write(data);
                });

                res.on('end', () => {
                    resolve(res.statusCode);
                });
            });

            req.on('error', (error) => {
                console.error(error);
            });

            req.end();
        });
    }
}
