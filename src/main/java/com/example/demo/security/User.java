package com.example.demo.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.Collection;
import java.util.Collections;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private String id;
    private String email;
    private String role; // ✅ ADD THIS FIELD

    // This helper method allows the filter to easily get authorities
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String formattedRole = (role != null && role.startsWith("ROLE_"))
                ? role
                : "ROLE_" + role;
        return Collections.singletonList(new SimpleGrantedAuthority(formattedRole));
    }
}