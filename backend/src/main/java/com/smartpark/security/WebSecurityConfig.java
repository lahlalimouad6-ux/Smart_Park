package com.smartpark.security;

import com.smartpark.security.jwt.AuthTokenFilter;
import com.smartpark.security.services.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableMethodSecurity
public class WebSecurityConfig {
    private static final Logger logger = LoggerFactory.getLogger(WebSecurityConfig.class);

    @Autowired
    UserDetailsServiceImpl userDetailsService;

    @Bean
    public AuthTokenFilter authenticationJwtTokenFilter() {
        return new AuthTokenFilter();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((request, response, authException) -> {
                        response.setStatus(401);
                        response.setCharacterEncoding("UTF-8");
                        response.setContentType("application/json;charset=UTF-8");
                        try (PrintWriter writer = response.getWriter()) {
                            writer.write("{\"message\":\"Non authentifié\"}");
                            writer.flush();
                        }
                    })
                    .accessDeniedHandler((request, response, accessDeniedException) -> {
                        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                        String principal = auth != null ? String.valueOf(auth.getPrincipal()) : "null";
                        String authorities = auth != null
                                ? auth.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.joining(","))
                                : "";
                        logger.warn("Access denied for {} {} principal={} authorities={}",
                                request.getMethod(), request.getRequestURI(), principal, authorities);
                        response.setStatus(403);
                        response.setCharacterEncoding("UTF-8");
                        response.setContentType("application/json;charset=UTF-8");
                        try (PrintWriter writer = response.getWriter()) {
                            writer.write("{\"message\":\"Accès refusé\",\"principal\":\"" + principal + "\",\"authorities\":\"" + authorities + "\"}");
                            writer.flush();
                        }
                    })
            )
            .authorizeHttpRequests(auth -> 
                auth.requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/cameras/video/**").permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/parkings/**").permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/reservations/qr/**").permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/reservations/qr-image/**").permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/reservations/token/**").permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/parkings/**").authenticated()
                    .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/parkings/**").authenticated()
                    .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/parkings/**").authenticated()
                    .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/parkings/**").authenticated()
                    .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                    .anyRequest().authenticated()
            );

        http.authenticationProvider(authenticationProvider());
        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*")); // Plus flexible que allowedOrigins
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*")); // Autorise tous les headers
        configuration.setExposedHeaders(List.of("Authorization", "x-auth-token"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
