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
 * Ads API {@code /api/v1/ads} (contract C1). Reading is open; creating and editing need a session
 * (enforced by the security filter chain) and ownership (enforced by {@link AdService}).
 */
@RestController
@RequestMapping("/api/v1/ads")
public class AdV1Controller {

    private final AdService service;

    public AdV1Controller(AdService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdView> list(CurrentUser caller) {
        return service.findAll(caller);
    }

    @GetMapping("/{id}")
    public AdView get(@PathVariable long id, CurrentUser caller) {
        return service.findById(id, caller);
    }

    @PostMapping
    public ResponseEntity<AdView> create(@Valid @RequestBody AdRequest request, CurrentUser caller) {
        AdView created = service.create(request, caller);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public AdView update(@PathVariable long id, @Valid @RequestBody AdRequest request, CurrentUser caller) {
        return service.update(id, request, caller);
    }
}
