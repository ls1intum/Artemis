/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class StudentDTO {
    public login!: string;
    public firstName!: string;
    public lastName!: string;
    public registrationNumber!: string;
    public email!: string;
}
