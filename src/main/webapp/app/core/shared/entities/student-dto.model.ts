/** An import row. Only one identifier is needed, and the server omits empty fields from unmatched rows. */
export class StudentDTO {
    declare public login?: string;
    declare public firstName?: string;
    declare public lastName?: string;
    declare public registrationNumber?: string;
    declare public email?: string;
    /** Optional: whether the imported user is a test/QA account (excluded from usage statistics). Undefined means "not provided" and leaves the flag unchanged. */
    declare public isTestUser?: boolean;
}
