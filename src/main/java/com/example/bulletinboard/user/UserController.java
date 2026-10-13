package com.example.bulletinboard.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserDirectoryService service;

    public UserController(UserDirectoryService service) {
        this.service = service;
    }

    // Only GET is mapped: other methods are answered with 405 (after the login and CSRF checks)
    @GetMapping
    public List<UserDirectoryEntry> list() {
        return service.findAll();
    }
}
