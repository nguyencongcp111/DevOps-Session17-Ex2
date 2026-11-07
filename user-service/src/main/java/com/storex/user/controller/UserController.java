package com.storex.user.controller;

import com.storex.user.dto.CreateUserRequest;
import com.storex.user.entity.User;
import com.storex.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<User> list() {
        log.debug("Listing all users");
        return userRepository.findAll();
    }

    @GetMapping("/{id}")
    public User get(@PathVariable Long id) {
        log.debug("Fetching user id={}", id);
        return userRepository.findById(id).orElseThrow(() -> {
            log.warn("User not found id={}", id);
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id);
        });
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public User create(@Valid @RequestBody CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Email already registered email={}", request.email());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered: " + request.email());
        }
        User saved = userRepository.save(new User(request.fullName(), request.email()));
        log.info("User created id={} email={}", saved.getId(), saved.getEmail());
        return saved;
    }
}
