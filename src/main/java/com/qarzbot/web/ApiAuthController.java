package com.qarzbot.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiAuthController {

    @Value("${web.admin.password}")
    private String adminPassword;

    public record LoginRequest(String password) {}

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest body,
                                                      HttpSession session) {
        if (adminPassword.equals(body.password())) {
            session.setAttribute("WEB_ADMIN", Boolean.TRUE);
            return ResponseEntity.ok(Map.of("ok", true));
        }
        return ResponseEntity.status(401).body(Map.of("ok", false));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        session.invalidate();
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpSession session) {
        // Interceptor already blocks unauthenticated requests to /api/me,
        // but we also handle the case inline for clarity.
        if (session.getAttribute("WEB_ADMIN") != null) {
            return ResponseEntity.ok(Map.of("authenticated", true));
        }
        return ResponseEntity.status(401).body(Map.of("authenticated", false));
    }
}
