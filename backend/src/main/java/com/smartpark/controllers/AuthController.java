package com.smartpark.controllers;

import com.smartpark.dto.JwtResponse;
import com.smartpark.dto.LoginRequest;
import com.smartpark.dto.SignupRequest;
import com.smartpark.models.ERole;
import com.smartpark.models.User;
import com.smartpark.repository.UserRepository;
import com.smartpark.security.jwt.JwtUtils;
import com.smartpark.security.services.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateJwtToken(authentication);

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());

            return ResponseEntity.ok(new JwtResponse(jwt,
                    userDetails.getId(),
                    userDetails.getEmail(),
                    roles));
        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            return ResponseEntity.status(401).body("{\"message\": \"Email ou mot de passe incorrect\"}");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"message\": \"Erreur interne du serveur: " + e.getMessage() + "\"}");
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body("{\"message\": \"Error: Email is already in use!\"}");
        }

        // Create new user's account
        User user = new User(signUpRequest.getNom(),
                signUpRequest.getPrenom(),
                signUpRequest.getEmail(),
                encoder.encode(signUpRequest.getPassword()),
                signUpRequest.getRole() != null && signUpRequest.getRole().equals("ADMIN") ? ERole.ADMIN : ERole.CONDUCTEUR);

        userRepository.save(user);

        return ResponseEntity.ok("{\"message\": \"User registered successfully!\"}");
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body("{\"message\": \"Non authentifié\"}");
        }

        Object principal = authentication.getPrincipal();
        Map<String, Object> payload = new HashMap<>();

        if (principal instanceof UserDetailsImpl userDetails) {
            payload.put("id", userDetails.getId());
            payload.put("email", userDetails.getEmail());
        } else {
            payload.put("email", authentication.getName());
        }

        List<String> roles = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toList());
        payload.put("roles", roles);

        return ResponseEntity.ok(payload);
    }
}
