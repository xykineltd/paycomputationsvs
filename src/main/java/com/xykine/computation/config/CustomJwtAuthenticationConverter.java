package com.xykine.computation.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String name = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");


        Collection<GrantedAuthority> authorities = AuthorityUtils.NO_AUTHORITIES;
        if (jwt.getClaims().containsKey("roles")) {
            List<String> roles = jwt.getClaimAsStringList("roles");
            authorities = roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .collect(Collectors.toList());
        }
        Map<String, Object> customAttributes = jwt.getClaims();

//        customAttributes.put("TenantID", jwt.getClaimAsString("TENANTID"));
        CustomUserDetails userDetails = new CustomUserDetails(name, email, authorities, customAttributes);
        return new UsernamePasswordAuthenticationToken(userDetails, jwt.getTokenValue(), authorities);
    }
}


