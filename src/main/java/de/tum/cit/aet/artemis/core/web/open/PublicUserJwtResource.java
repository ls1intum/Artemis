package de.tum.cit.aet.artemis.core.web.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import de.tum.cit.aet.artemis.communication.service.notifications.MailService;
import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.core.exception.CaptchaRequiredException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.user.UserCreationService;
import de.tum.cit.aet.artemis.core.util.SamlHeadersUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.dto.vm.LoginVM;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.UserNotActivatedException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;
import de.tum.cit.aet.artemis.core.service.connectors.SAML2Service;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;

/**
 * REST controller to authenticate users.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/public/")
public class PublicUserJwtResource {

    private static final Logger log = LoggerFactory.getLogger(PublicUserJwtResource.class);

    private final JWTCookieService jwtCookieService;

    private final AuthenticationManager authenticationManager;

    private final Optional<SAML2Service> saml2Service;

    // CodeAbility: Added the following four attributes
    @Value("${jhipster.clientApp.name}")
    private String applicationName;
    private final UserRepository userRepository;
    private final UserCreationService userCreationService;
    private final MailService mailService;

    public PublicUserJwtResource(JWTCookieService jwtCookieService, AuthenticationManager authenticationManager, Optional<SAML2Service> saml2Service,
                                 UserRepository userRepository, UserCreationService userCreationService, MailService mailService) {
        this.jwtCookieService = jwtCookieService;
        this.authenticationManager = authenticationManager;
        this.saml2Service = saml2Service;
        this.userRepository = userRepository;
        this.userCreationService = userCreationService;
        this.mailService = mailService;
    }

    /**
     * Authorizes a User
     *
     * @param loginVM   user credentials View Mode
     * @param userAgent User Agent
     * @param response  HTTP response
     * @return the ResponseEntity with status 200 (ok), 401 (unauthorized) or 403 (Captcha required)
     */
    @PostMapping("authenticate")
    @EnforceNothing
    public ResponseEntity<Map<String, String>> authorize(@Valid @RequestBody LoginVM loginVM, @RequestHeader("User-Agent") String userAgent, HttpServletResponse response) {

        var username = loginVM.getUsername();
        var password = loginVM.getPassword();
        SecurityUtils.checkUsernameAndPasswordValidity(username, password);

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);

        try {
            authenticationToken.setDetails(Pair.of("userAgent", userAgent));
            Authentication authentication = authenticationManager.authenticate(authenticationToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            boolean rememberMe = loginVM.isRememberMe() != null && loginVM.isRememberMe();

            ResponseCookie responseCookie = jwtCookieService.buildLoginCookie(rememberMe);
            response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());

            return ResponseEntity.ok(Map.of("access_token", responseCookie.getValue()));
        }
        catch (BadCredentialsException ex) {
            log.warn("Wrong credentials during login for user {}", loginVM.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * Authorizes a User logged in with SAML2
     *
     * @param body     the body of the request. "true" to remember the user.
     * @param response HTTP response
     * @return the ResponseEntity with status 200 (ok), 401 (unauthorized) or 403 (user not activated)
     */
    @PostMapping("saml2")
    @EnforceNothing
    public ResponseEntity<Void> authorizeSAML2(@RequestBody final String body, HttpServletResponse response) {
        if (saml2Service.isEmpty()) {
            throw new AccessForbiddenException("SAML2 is disabled");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof final Saml2AuthenticatedPrincipal principal)) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        log.debug("SAML2 authentication: {}", authentication);

        try {
            authentication = saml2Service.get().handleAuthentication(authentication, principal);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        catch (UserNotActivatedException e) {
            // If the exception is not caught a 401 is returned.
            // That does not match the actual reason and would trigger authentication in the client
            return ResponseEntity.status(HttpStatus.FORBIDDEN).header("X-artemisApp-error", e.getMessage()).build();
        }

        final boolean rememberMe = Boolean.parseBoolean(body);
        ResponseCookie responseCookie = jwtCookieService.buildLoginCookie(rememberMe);
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());

        return ResponseEntity.ok().build();
    }

    /**
     * Removes the cookie containing the jwt
     * Is public to make sure a logout can even occur when there is some issue with the authentication
     *
     * @param request  HTTP request
     * @param response HTTP response
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("logout")
    @EnforceNothing
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) throws ServletException {
        request.logout();
        // Logout needs to build the same cookie (secure, httpOnly and sameSite='Lax') or browsers will ignore the header and not unset the cookie
        ResponseCookie responseCookie = jwtCookieService.buildLogoutCookie();
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
        return ResponseEntity.ok().build();
    }

    // CodeAbility: Added all methods below

    @PostMapping("authenticate-shib")
    @EnforceNothing
    public ResponseEntity<Void> authorize(@RequestBody LoginVM loginVM, @RequestHeader HttpHeaders headers, @RequestHeader("User-Agent") String userAgent,
                                          HttpServletResponse response) {
        // URL encoded headers. Need to be decoded
        HttpHeaders decodedHeaders = new HttpHeaders();
        headers.forEach((key, value) -> decodedHeaders.addAll(key, decodeURLList(value)));

        if (headers.getFirst("ajp_shib-session-id") == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "No shibboleth session could be found in request!", "SessionNotFound")).body(null);
        }

        List<String> missingHeaders = checkRequiredAttributes(decodedHeaders);
        if (!missingHeaders.isEmpty()) {
            log.error("Login with EduId is missing some attributes: [ " + String.join(", ", missingHeaders) + " ]");
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "One or more attributes couldn't be found in request!", "AttributeNotFound"))
                .body(null);
        }

        Optional<User> user = userRepository.findOneWithGroupsAndAuthoritiesByLogin(eppnHeaderToLogin(decodedHeaders.getFirst(SamlHeadersUtil.EPPN.header)));

        // check if not registered yet
        if (user.isEmpty()) {
            user = createSAMLUserFromHeaders(decodedHeaders);
        }

        // TODO: REPLACE WITH NATIVE SPRING-SAML INTEGRATION
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(user.get().getLogin(), "",
            toGrantedAuthorities(user.get().getAuthorities()));
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        try {
            authenticationToken.setDetails(Pair.of("userAgent", userAgent));
            // TODO: RE-ENABLE WHEN SPRING-SAML IS USED
            // Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
            boolean rememberMe = loginVM.isRememberMe() != null && loginVM.isRememberMe();

            ResponseCookie responseCookie = jwtCookieService.buildLoginCookie(rememberMe);
            response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());

            return ResponseEntity.ok().build();
        }
        catch (CaptchaRequiredException ex) {
            log.warn("CAPTCHA required in JIRA during login for user " + user.get().getLogin());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).header("X-artemisApp-error", ex.getMessage()).build();
        }
    }

    /**
     * Method creating a new User from the EduID headers provided
     *
     * This is a temporary workaround until the native SPRING-SAML implementation is used
     *
     * @param decodedHeaders containing the user's data to use
     */
    private Optional<User> createSAMLUserFromHeaders(HttpHeaders decodedHeaders) {
        ManagedUserVM userDTO = new ManagedUserVM();
        userDTO.setLogin(eppnHeaderToLogin(decodedHeaders.getFirst(SamlHeadersUtil.EPPN.header)));
        // use "displayName" attribute if equivalent "givenname" and "sn" are not available
        if (decodedHeaders.getFirst(SamlHeadersUtil.GIVEN_NAME.header).isEmpty() || decodedHeaders.getFirst(SamlHeadersUtil.SN.header).isEmpty()) {
            userDTO.setFirstName(decodedHeaders.getFirst(SamlHeadersUtil.DISPLAY_NAME.header).split(" ")[0]);
            userDTO.setLastName(decodedHeaders.getFirst(SamlHeadersUtil.DISPLAY_NAME.header).split(" ")[1]);
        }
        else {
            userDTO.setFirstName(decodedHeaders.getFirst(SamlHeadersUtil.GIVEN_NAME.header));
            userDTO.setLastName(decodedHeaders.getFirst(SamlHeadersUtil.SN.header));
        }
        userDTO.setEmail(decodedHeaders.getFirst(SamlHeadersUtil.MAIL.header));
        userDTO.setLangKey("de");

        // check if email already used
        if (userRepository.findOneByEmailIgnoreCase(userDTO.getEmail()).isPresent()) {
            Response.ResponseBuilder responseBuilder = Response.status(Response.Status.BAD_REQUEST).header("X-artemisApp-error", "Email already used by another account");
            throw new BadRequestException(responseBuilder.build());
        }

        // add authorities basing on values provided by eduPersonAffiliation if available
        Set<String> auth = new HashSet<>();
        auth.add(Role.STUDENT.getAuthority());

        userDTO.setAuthorities(auth);

        userCreationService.createUser(userDTO);
        Optional<User> user = userRepository.findOneWithGroupsAndAuthoritiesByLogin(eppnHeaderToLogin(decodedHeaders.getFirst(SamlHeadersUtil.EPPN.header)));   // reload user after
        // creation
        if (user.isEmpty()) {
            throw new EntityNotFoundException("Error creating user");
        }
        mailService.sendCreationEmail(user.get());
        return user;
    }

    private static Collection<GrantedAuthority> toGrantedAuthorities(final Collection<Authority> authorities) {
        return authorities.stream().map(Authority::getName).map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
    }

    // Utility methods
    /**
     * Check if all required attributes are included in the request headers
     *
     * @param headers the request headers
     * @return true if all included, false if any is missing
     */
    private List<String> checkRequiredAttributes(HttpHeaders headers) {
        ArrayList<String> missingHeaders = new ArrayList<>();
        if (headers.getFirst(SamlHeadersUtil.EPPN.header) == null || headers.getFirst(SamlHeadersUtil.EPPN.header).isEmpty()) {
            missingHeaders.add(SamlHeadersUtil.EPPN.header);
        }
        if (headers.getFirst(SamlHeadersUtil.DISPLAY_NAME.header) == null || headers.getFirst(SamlHeadersUtil.DISPLAY_NAME.header).isEmpty()) {
            if ((headers.getFirst(SamlHeadersUtil.GIVEN_NAME.header) == null || headers.getFirst(SamlHeadersUtil.GIVEN_NAME.header).isEmpty())
                || (headers.getFirst(SamlHeadersUtil.SN.header) == null || headers.getFirst(SamlHeadersUtil.SN.header).isEmpty())) {
                missingHeaders.add("ajp_displayname or ajp_givenname and ajp_sn");
            }
        }
        if (headers.getFirst(SamlHeadersUtil.MAIL.header) == null || headers.getFirst(SamlHeadersUtil.MAIL.header).isEmpty()) {
            missingHeaders.add(SamlHeadersUtil.MAIL.header);
        }
        return missingHeaders;
    }

    /**
     * Convert eppn attribute to meet login regex requirements
     *
     * @param ajp_eppn the attribute to convert
     * @return the converted string
     */
    private String eppnHeaderToLogin(String ajp_eppn) {
        return ajp_eppn.replace("@", "_");
    }

    /**
     * Method used to decode the list of URL encoded header values from Shibboleth
     * to UTF-8
     *
     * @param headers to decode
     * @return list of decoded values
     */
    private List<String> decodeURLList(List<String> headers) {
        List<String> result = new ArrayList<>();
        headers.forEach((header) -> {
            result.add(URLDecoder.decode(header, StandardCharsets.UTF_8));
        });
        return result;
    }

}
