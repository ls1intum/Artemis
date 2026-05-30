package de.tum.cit.aet.artemis.account.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.config.AccountLegacyRestPaths;
import de.tum.cit.aet.artemis.core.dto.UserDTO;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;

/**
 * Legacy compatibility controller that keeps the pre-9.3 {@code PUT api/core/account} endpoint alive.
 * <p>
 * The canonical endpoint now lives in {@link AccountResource} as {@code PUT api/account/basic-information} (with the
 * legacy alias {@code api/core/account/basic-information}). Folding the former {@code account/} resource segment into
 * {@link AccountLegacyRestPaths#CORE_ACCOUNT_PREFIX} preserves every {@code api/core/account/<sub-resource>} path, but
 * it cannot reproduce the bare {@code api/core/account} URL, which carries no sub-resource segment. Deployed clients
 * (iOS, Android, older web) still call that bare URL to update the current user's basic information, so this shim maps
 * it explicitly through the {@code api/core/} legacy prefix and delegates to {@link AccountResource#saveAccount}.
 * <p>
 * TODO: Remove this controller once all clients have migrated to {@code api/account/basic-information}.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@SuppressWarnings("deprecation")
@RequestMapping(AccountLegacyRestPaths.CORE_PREFIX)
public class AccountLegacyResource {

    private final AccountResource accountResource;

    public AccountLegacyResource(AccountResource accountResource) {
        this.accountResource = accountResource;
    }

    /**
     * PUT api/core/account : deprecated legacy alias of {@code PUT api/account/basic-information}. Updates the basic
     * information (name, email, language, image) of the current user's account.
     *
     * @param userDTO the current user information.
     * @return the ResponseEntity with status 200 (OK) when the user information is updated.
     */
    @Deprecated(forRemoval = true, since = "9.3")
    @PutMapping("account")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> saveAccountLegacy(@Valid @RequestBody UserDTO userDTO) {
        return accountResource.saveAccount(userDTO);
    }
}
