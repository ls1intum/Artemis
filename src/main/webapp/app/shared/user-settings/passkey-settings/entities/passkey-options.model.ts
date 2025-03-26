// TODO guidelines type vs interface
export interface PasskeyOptions {
    rp: {
        id: string;
        name: string;
    };
    challenge: string;
    pubKeyCredParams: Array<{
        type: string;
        alg: number;
    }>;
    excludeCredentials: any[];
    authenticatorSelection: {
        requireResidentKey: boolean;
    };
    attestation: string;
    extensions: {
        uvm: boolean;
        credProps: boolean;
    };
}
