package com.example.bulletinboard.ad;

import com.example.bulletinboard.account.AccountService;
import com.example.bulletinboard.auth.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ads with ownership. The caller is always passed in explicitly by the controller; the owner of a new
 * ad comes only from the caller, never from the request body, and only the owner may edit
 * (FR4.2-FR4.5, ADR-005).
 */
@Service
@Transactional(readOnly = true)
public class AdService {

    static final String UNKNOWN_AUTHOR = "Unknown";

    private final AdRepository repository;
    private final AccountService accounts;

    public AdService(AdRepository repository, AccountService accounts) {
        this.repository = repository;
        this.accounts = accounts;
    }

    // --- /api/v1/ads (contract C1) ---

    /**
     * All ads, newest first, with poster names read in one batched lookup (NFR5.5).
     */
    public List<AdView> findAll(CurrentUser caller) {
        List<Ad> ads = repository.findAllByOrderByCreatedAtDescIdDesc();
        Set<Long> ownerIds = ads.stream().map(Ad::getOwnerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = accounts.displayNames(ownerIds);
        return ads.stream().map(ad -> toView(ad, names, caller)).toList();
    }

    public AdView findById(long id, CurrentUser caller) {
        return toView(getAd(id), caller);
    }

    @Transactional
    public AdView create(AdRequest request, CurrentUser caller) {
        return toView(createAd(request, caller), caller);
    }

    @Transactional
    public AdView update(long id, AdRequest request, CurrentUser caller) {
        return toView(updateAd(id, request, caller), caller);
    }

    // --- legacy /api/ads (contract C2): old response shape, same rules ---

    public List<AdResponse> findAll() {
        return repository.findAllByOrderByCreatedAtDescIdDesc().stream().map(AdResponse::from).toList();
    }

    public AdResponse findById(long id) {
        return AdResponse.from(getAd(id));
    }

    @Transactional
    public AdResponse createLegacy(AdRequest request, CurrentUser caller) {
        return AdResponse.from(createAd(request, caller));
    }

    @Transactional
    public AdResponse updateLegacy(long id, AdRequest request, CurrentUser caller) {
        return AdResponse.from(updateAd(id, request, caller));
    }

    // --- shared rules ---

    private Ad createAd(AdRequest request, CurrentUser caller) {
        long ownerId = caller.requireAccountId();
        return repository.save(new Ad(request.title(), request.description(), ownerId));
    }

    private Ad updateAd(long id, AdRequest request, CurrentUser caller) {
        long callerId = caller.requireAccountId();
        Ad ad = getAd(id);
        if (ad.getOwnerId() == null) {
            throw new UnownedAdException();
        }
        if (ad.getOwnerId() != callerId) {
            throw new NotOwnerException();
        }
        ad.update(request.title(), request.description());
        // Flush so @PreUpdate sets updatedAt before the response is built
        return repository.saveAndFlush(ad);
    }

    private Ad getAd(long id) {
        return repository.findById(id).orElseThrow(() -> new AdNotFoundException(id));
    }

    private AdView toView(Ad ad, CurrentUser caller) {
        Map<Long, String> names = ad.getOwnerId() == null ? Map.of() : accounts.displayNames(Set.of(ad.getOwnerId()));
        return toView(ad, names, caller);
    }

    private static AdView toView(Ad ad, Map<Long, String> names, CurrentUser caller) {
        String authorName = ad.getOwnerId() == null ? UNKNOWN_AUTHOR : names.getOrDefault(ad.getOwnerId(), UNKNOWN_AUTHOR);
        return new AdView(ad.getId(), ad.getTitle(), ad.getDescription(), ad.getCreatedAt(), ad.getUpdatedAt(),
                authorName, caller.owns(ad.getOwnerId()));
    }
}
