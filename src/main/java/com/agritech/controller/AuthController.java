package com.agritech.controller;

import com.agritech.model.AppUser;
import com.agritech.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepo;

    public AuthController(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        String password = body.getOrDefault("password", "");

        if (email.isBlank() || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and password are required.");
        }

        // Demo user convenience auto-creation if not in DB
        if ("demo@agritech.org".equalsIgnoreCase(email) && !userRepo.existsByEmail(email)) {
            AppUser demo = new AppUser(
                "demo@agritech.org",
                "demo123",
                "Ramesh Patel",
                "Krishna Organic Farm",
                "Warangal, Telangana",
                12.5
            );
            userRepo.save(demo);
        }

        Optional<AppUser> userOpt = userRepo.findByEmail(email);
        if (userOpt.isEmpty()) {
            // Allow login by creating if it's the demo email or throw unauthorized
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }

        AppUser user = userOpt.get();
        if (!password.equals(user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }

        return formatUserResponse(user);
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> body) {
        String email = String.valueOf(body.getOrDefault("email", "")).trim().toLowerCase();
        String password = String.valueOf(body.getOrDefault("password", ""));
        String fullName = String.valueOf(body.getOrDefault("fullName", "Farmer"));
        String farmName = String.valueOf(body.getOrDefault("farmName", "My Farm"));
        String location = String.valueOf(body.getOrDefault("location", "Telangana, India"));
        Double landAcres = null;
        try {
            if (body.get("landAcres") != null) {
                landAcres = Double.valueOf(body.get("landAcres").toString());
            }
        } catch (Exception ignored) {}

        if (email.isBlank() || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and password are required.");
        }

        if (userRepo.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }

        AppUser newUser = new AppUser(email, password, fullName, farmName, location, landAcres != null ? landAcres : 5.0);
        userRepo.save(newUser);

        return formatUserResponse(newUser);
    }

    @PutMapping("/profile/{id}")
    public Map<String, Object> updateProfile(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        AppUser user = userRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (body.containsKey("fullName")) user.setFullName(String.valueOf(body.get("fullName")));
        if (body.containsKey("farmName")) user.setFarmName(String.valueOf(body.get("farmName")));
        if (body.containsKey("location")) user.setLocation(String.valueOf(body.get("location")));
        if (body.containsKey("landAcres") && body.get("landAcres") != null) {
            try { user.setLandAcres(Double.valueOf(body.get("landAcres").toString())); } catch (Exception ignored) {}
        }
        userRepo.save(user);
        return formatUserResponse(user);
    }

    private Map<String, Object> formatUserResponse(AppUser user) {
        Map<String, Object> res = new HashMap<>();
        res.put("id", user.getId());
        res.put("email", user.getEmail());
        res.put("fullName", user.getFullName());
        res.put("farmName", user.getFarmName());
        res.put("location", user.getLocation());
        res.put("landAcres", user.getLandAcres() != null ? user.getLandAcres() : 0.0);
        res.put("token", "agritech_token_" + user.getId() + "_" + System.currentTimeMillis());
        return res;
    }
}
