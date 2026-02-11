package com.example.demo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // ✅ Add this
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j // ✅ Enable Logging
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;
        
        // ✅ LOG 1: Prove the request hit the server
        log.info("🛡️ [FILTER] Request Received: {} {}", request.getMethod(), request.getRequestURI());

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("⚠️ [FILTER] No valid Authorization header found. Proceeding as Anonymous.");
            filterChain.doFilter(request, response);
            return;
        }

        try {
            jwt = authHeader.substring(7);
            userEmail = jwtService.extractUsername(jwt);
            String rawRole = jwtService.extractRole(jwt);
            String userId = jwtService.extractClaim(jwt, claims -> claims.get("userId", String.class));

            log.info("👤 [FILTER] Token Decoded. User: {}, Role: {}", userEmail, rawRole);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (jwtService.isTokenValid(jwt, userEmail)) {

                    String formattedRole = (rawRole != null && rawRole.startsWith("ROLE_"))
                            ? rawRole
                            : "ROLE_" + rawRole;

                    log.info("✅ [FILTER] Authenticating User with Authority: {}", formattedRole);

                    User user = User.builder()
                    .id(userId)  // <--- CRITICAL FIX
                    .email(userEmail)
                    .role(rawRole)
                    .build();
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority(formattedRole))
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    log.error("❌ [FILTER] Token validation failed.");
                }
            }
        } catch (Exception e) {
            log.error("❌ [FILTER] JWT Error: {}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }
}
