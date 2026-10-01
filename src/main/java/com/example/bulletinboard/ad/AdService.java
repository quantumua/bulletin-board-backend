package com.example.bulletinboard.ad;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdService {

    private final AdRepository repository;

    public AdService(AdRepository repository) {
        this.repository = repository;
    }

    public List<AdResponse> findAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(AdResponse::from).toList();
    }

    public AdResponse findById(long id) {
        return AdResponse.from(getAd(id));
    }

    @Transactional
    public AdResponse create(AdRequest request) {
        return AdResponse.from(repository.save(new Ad(request.title(), request.description())));
    }

    @Transactional
    public AdResponse update(long id, AdRequest request) {
        Ad ad = getAd(id);
        ad.update(request.title(), request.description());
        // Flush so @PreUpdate sets updatedAt before the response is built
        return AdResponse.from(repository.saveAndFlush(ad));
    }

    private Ad getAd(long id) {
        return repository.findById(id).orElseThrow(() -> new AdNotFoundException(id));
    }
}
