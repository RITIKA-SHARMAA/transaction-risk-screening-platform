package com.ritikasharma.risk.security;

import com.ritikasharma.risk.domain.AppUser;
import com.ritikasharma.risk.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;

/** Login-time view of an {@link AppUser}; exists only while credentials are checked. */
final class AppUserPrincipal implements UserDetails {

    private final String username;
    private final String passwordHash;
    private final String merchantId;
    private final boolean enabled;
    private final Set<Role> roles;

    AppUserPrincipal(AppUser user) {
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.merchantId = user.getMerchantId();
        this.enabled = user.isEnabled();
        this.roles = user.getRoles();
    }

    String merchantId() {
        return merchantId;
    }

    Set<Role> roles() {
        return roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
