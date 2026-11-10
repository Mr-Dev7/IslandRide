package com.islandride.core.security;

import com.islandride.identityaccess.domain.User;
import com.islandride.identityaccess.domain.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String publicId;
    private final String email;
    private final String password;
    private final String firstName;
    private final String lastName;
    private final UserStatus status;
    private final Long businessId; // Contextual business ID for partner requests
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(Long id,
                         String publicId,
                         String email,
                         String password,
                         String firstName,
                         String lastName,
                         UserStatus status,
                         Long businessId,
                         Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.publicId = publicId;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = status;
        this.businessId = businessId;
        this.authorities = authorities;
    }

    public static UserPrincipal create(User user, Long businessId) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        if (user.getRoles() != null) {
            user.getRoles().forEach(role -> {
                authorities.add(new SimpleGrantedAuthority(role.getName()));
                if (role.getPermissions() != null) {
                    role.getPermissions().forEach(perm ->
                        authorities.add(new SimpleGrantedAuthority(perm.getName()))
                    );
                }
            });
        }

        return new UserPrincipal(
                user.getId(),
                user.getPublicId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus(),
                businessId,
                authorities
        );
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Long getBusinessId() {
        return businessId;
    }

    public boolean hasRole(String roleName) {
        String authorityName = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
        return authorities.stream()
                .anyMatch(auth -> auth.getAuthority().equals(authorityName));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status != UserStatus.DISABLED && status != UserStatus.SUSPENDED;
    }
}
