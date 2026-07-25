package com.travelalbum.security.userdetails;

import com.travelalbum.entity.User;
import com.travelalbum.enums.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

@Getter
public class UserPrincipal implements Serializable {

    private final Long id;
    private final String email;
    private final Role role;

    public UserPrincipal(Long id, String email, Role role) {
        this.id = id;
        this.email = email;
        this.role = role;
    }

    public static UserPrincipal from(User u) {
        return new UserPrincipal(u.getId(), u.getEmail(), u.getRole());
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
