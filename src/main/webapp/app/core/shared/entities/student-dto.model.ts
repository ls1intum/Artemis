/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class StudentDTO {
    public login!: string;
    public firstName!: string;
    public lastName!: string;
    public registrationNumber!: string;
    public email!: string;
    /** Optional: whether the imported user is a test/QA account (excluded from usage statistics). Undefined means "not provided" and leaves the flag unchanged. */
    public isTestUser?: boolean;
}
