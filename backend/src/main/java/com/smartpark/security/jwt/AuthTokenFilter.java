package com.smartpark.security.jwt;

import com.smartpark.security.services.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class AuthTokenFilter extends OncePerRequestFilter {
    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    private static final Logger logger = LoggerFactory.getLogger(AuthTokenFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean shouldLog = request.getRequestURI() != null
                && request.getRequestURI().startsWith("/api/parkings")
                && !"GET".equalsIgnoreCase(request.getMethod())
                && !"OPTIONS".equalsIgnoreCase(request.getMethod());
        if (shouldLog) {
            logger.info("Processing request: {} {}", request.getMethod(), request.getRequestURI());
        } else {
            logger.debug("Processing request: {} {}", request.getMethod(), request.getRequestURI());
        }
        try {
            String jwt = parseJwt(request);
            if (jwt == null) {
                if (shouldLog) {
                    logger.info("No JWT found in Authorization header for {} {}", request.getMethod(), request.getRequestURI());
                } else {
                    logger.debug("No JWT found in Authorization header for {} {}", request.getMethod(), request.getRequestURI());
                }
            }
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                String username = jwtUtils.getUserNameFromJwtToken(jwt);

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                if (shouldLog) {
                    logger.info("Authenticated user {} with authorities {}", username, userDetails.getAuthorities());
                } else {
                    logger.debug("Authenticated user {} with authorities {}", username, userDetails.getAuthorities());
                }
            } else if (jwt != null) {
                if (shouldLog) {
                    logger.info("JWT validation failed for {} {}", request.getMethod(), request.getRequestURI());
                } else {
                    logger.debug("JWT validation failed for {} {}", request.getMethod(), request.getRequestURI());
                }
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: {}", e);
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        if (!StringUtils.hasText(headerAuth)) {
            return null;
        }

        String trimmed = headerAuth.trim();
        if (trimmed.regionMatches(true, 0, "Bearer", 0, "Bearer".length())) {
            String[] parts = trimmed.split("\\s+");
            if (parts.length >= 2) {
                return parts[1].trim();
            }
        }

        return null;
    }
}
