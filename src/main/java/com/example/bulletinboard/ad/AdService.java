package com.example.bulletinboard.ad;

import com.example.bulletinboard.user.AppUser;
import com.example.bulletinboard.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdService {

    private final AdRepository repository;
    private final UserRepository userRepository;

    public AdService(AdRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    public List<AdResponse> findAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(AdResponse::from).toList();
    }

    public AdResponse findById(long id) {
        return AdResponse.from(getAd(id));
    }

    @Transactional
    public AdResponse create(AdRequest request, String username) {
        AppUser owner = userRepository.findByUsernameLower(AppUser.normalize(username)).orElseThrow();
        return AdResponse.from(repository.save(new Ad(request.title(), request.description(), owner)));
    }

    @Transactional
    public AdResponse update(long id, AdRequest request, String username) {
        Ad ad = getAd(id);
        if (ad.getOwner() == null || !ad.getOwner().getUsername().equalsIgnoreCase(username)) {
            throw new AdForbiddenException(id);
        }
        ad.update(request.title(), request.description());
        // Flush so @PreUpdate sets updatedAt before the response is built
        return AdResponse.from(repository.saveAndFlush(ad));
    }

    private Ad getAd(long id) {
        return repository.findById(id).orElseThrow(() -> new AdNotFoundException(id));
    }
}
