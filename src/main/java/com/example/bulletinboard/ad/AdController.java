package com.example.bulletinboard.ad;

import com.example.bulletinboard.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/**
 * Legacy ads API {@code /api/ads} (contract C2, deprecated): the old response shape with the same
 * login, ownership and anti-forgery rules as {@code /api/v1/ads} (NFR5.3). Every response carries the
 * {@code Deprecation} and {@code Link} headers ({@link LegacyAdsDeprecationFilter}).
 */
@RestController
@RequestMapping("/api/ads")
public class AdController {

    private final AdService service;

    public AdController(AdService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdResponse> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public AdResponse get(@PathVariable long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<AdResponse> create(@Valid @RequestBody AdRequest request, CurrentUser caller) {
        AdResponse created = service.createLegacy(request, caller);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public AdResponse update(@PathVariable long id, @Valid @RequestBody AdRequest request, CurrentUser caller) {
        return service.updateLegacy(id, request, caller);
    }
}
