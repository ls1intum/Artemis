package de.tum.cit.aet.artemis.core.security.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import de.tum.cit.aet.artemis.core.repository.UserRepository;

/**
 * Servlet filter that authenticates requests using a bot API key provided in the X-Bot-Api-Key header.
 * If the header is present and maps to a valid bot user, the SecurityContext is populated with the bot user.
 * If the header is absent, the filter is a no-op and the request proceeds to the next filter.
 */
@Component
public class BotApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Bot-Api-Key";

    private final UserRepository userRepository;

    public BotApiKeyFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey != null && !apiKey.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
            String hash = sha256Hex(apiKey);
            var botUserOpt = userRepository.findBotByApiKeyHash(hash);

            if (botUserOpt.isPresent()) {
                var user = botUserOpt.get();
                var authorities = user.getAuthorities().stream().map(a -> new org.springframework.security.core.authority.SimpleGrantedAuthority(a.getName())).toList();
                var authentication = new UsernamePasswordAuthenticationToken(user.getLogin(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bot API key");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Computes the SHA-256 hash of the given input string and returns it as a lowercase hex string.
     *
     * @param input the string to hash
     * @return the hex-encoded SHA-256 hash
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
