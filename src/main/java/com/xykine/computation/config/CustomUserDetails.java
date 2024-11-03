package com.xykine.computation.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Map;

public class CustomUserDetails implements UserDetails {

    private String username;
    private String email;
    private Collection<? extends GrantedAuthority> authorities;
    private Map<String, Object> customAttributes;

    public CustomUserDetails(String username, String email, Collection<? extends GrantedAuthority> authorities, Map<String, Object> customAttributes) {
        this.username = username;
        this.email = email;
        this.authorities = authorities;
        this.customAttributes = customAttributes;
    }

    public String getEmail() {
        return email;
    }

    public Object getCustomAttribute(String key) {
        return customAttributes.get(key);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";  // Password is not needed for JWT-based authentication
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}

