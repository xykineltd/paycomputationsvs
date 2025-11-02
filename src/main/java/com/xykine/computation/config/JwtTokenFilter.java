package com.xykine.computation.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.xykine.payroll.model.UserRole;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Profile("!test")
@Component
public class JwtTokenFilter extends OncePerRequestFilter {

    private final JwtDecoder jwtDecoder;

    public JwtTokenFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Jwt jwt = jwtDecoder.decode(token);

            String email = jwt.getClaimAsString("email");
            String name = jwt.getClaimAsString("name");
            List<String> realmRoles = jwt.getClaimAsStringList("roles");

            // Filter roles based on UserRole enum
            List<String> filteredRoles = filterRolesFromJwt(realmRoles);

            // Convert roles to GrantedAuthority objects
            Collection<SimpleGrantedAuthority> authorities = filteredRoles.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            // Retrieve custom attributes
            Map<String, Object> customAttributes = new HashMap<>();
            customAttributes.put("EmployeeID", jwt.getClaimAsString("EmployeeID"));
            customAttributes.put("CompanyID", jwt.getClaimAsString("CompanyID"));

            // Create CustomUserDetails object
            CustomUserDetails userDetails = new CustomUserDetails(name, email, authorities, customAttributes);

            // Create a UsernamePasswordAuthenticationToken with the userDetails and the Jwt token
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());

            // Set the authentication in the SecurityContextHolder
            SecurityContextHolder.getContext().setAuthentication(authentication);


            CustomUserDetails userDetails1 = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            String emailFromJwt = userDetails1.getEmail();
            String employeeID = (String) userDetails1.getCustomAttribute("EmployeeID");
            String companyID = (String) userDetails1.getCustomAttribute("CompanyID");

            // Now you can use these values in your application logic
            System.out.println("Email: " + emailFromJwt);
            System.out.println("roles: " + Arrays.toString(userDetails1.getAuthorities().toArray()));
            System.out.println("EmployeeID: " + employeeID);
            System.out.println("CompanyID: " + companyID);
        }
        // Continue the filter chain
        filterChain.doFilter(request, response);
    }

    private List<String> filterRolesFromJwt(List<String> realmRoles) {
        final Set<String> ALLOWED_ROLES = EnumSet.allOf(UserRole.class)
                .stream()
                .map(UserRole::name)
                .collect(Collectors.toSet());

        // Filter the roles based on those defined in the UserRole enum
        return realmRoles.stream()
                .filter(ALLOWED_ROLES::contains)
                .collect(Collectors.toList());
    }
}

