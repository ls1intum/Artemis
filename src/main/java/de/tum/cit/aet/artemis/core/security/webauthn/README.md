### Files in this folder are just a workaround to get the WebAuthn working with Spring Security

We are using [Spring Security (when writing this 6.4.4)](https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html) for the WebAuthn Passkey authentication.

For our authentication to work, we need to set a JWT Token on successful authentication. Unfortunately, 
in the current 6.4.4 version
the Spring Security Webauthn interface does not seem to offer a built-in customization option for the 
`HttpMessageConverterAuthenticationSuccessHandler.onAuthenticationSuccess`.

`ArtemisWebAuthnConfigurer` is a modification of the SpringSecurity `WebAuthnConfigurer` as we want to define a custom 
`HttpMessageConverterAuthenticationSuccessHandler.onAuthenticationSuccess`.
If Spring Security adds a customization option (which I would assume to happen in the near future), we might be able to 
directly use the SpringSecurity `WebAuthnConfigurer`.
