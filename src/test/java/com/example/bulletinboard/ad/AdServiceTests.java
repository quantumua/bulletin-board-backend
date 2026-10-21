package com.example.bulletinboard.ad;

import com.example.bulletinboard.account.AccountService;
import com.example.bulletinboard.account.UserAccount;
import com.example.bulletinboard.account.UserAccountRepository;
import com.example.bulletinboard.auth.CurrentUser;
import com.example.bulletinboard.auth.NotAuthenticatedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class AdServiceTests {

    @Autowired
    private AdService adService;

    @Autowired
    private AdRepository adRepository;

    @Autowired
    private UserAccountRepository accountRepository;

    @MockitoSpyBean
    private AccountService accountService;

    private CurrentUser ann;
    private CurrentUser bob;

    @BeforeEach
    void setUp() {
        adRepository.deleteAll();
        accountRepository.deleteAll();
        ann = CurrentUser.of(saveAccount("ann@example.com", "Ann").getId());
        bob = CurrentUser.of(saveAccount("bob@example.com", "Bob").getId());
    }

    @Test
    void createSetsTheOwnerFromTheCaller() {
        AdView created = adService.create(new AdRequest("Bike", "Almost new"), ann);

        assertThat(adRepository.findById(created.id()).orElseThrow().getOwnerId()).isEqualTo(ann.accountId());
        assertThat(created.authorName()).isEqualTo("Ann");
        assertThat(created.ownedByMe()).isTrue();
    }

    @Test
    void ownerCanEdit() {
        Ad ad = adRepository.save(new Ad("Bike", "Old", ann.accountId()));

        AdView updated = adService.update(ad.getId(), new AdRequest("Red bike", "Freshly painted"), ann);

        assertThat(updated.title()).isEqualTo("Red bike");
        assertThat(adRepository.findById(ad.getId()).orElseThrow().getDescription()).isEqualTo("Freshly painted");
    }

    @Test
    void nonOwnerEditIsRefusedAndTheAdIsUnchanged() {
        Ad ad = adRepository.save(new Ad("Bike", "Old", ann.accountId()));

        assertThatThrownBy(() -> adService.update(ad.getId(), new AdRequest("Hacked", "Hacked"), bob))
                .isInstanceOf(NotOwnerException.class);
        assertThat(adRepository.findById(ad.getId()).orElseThrow().getTitle()).isEqualTo("Bike");
    }

    @Test
    void unownedAdCannotBeEditedByAnyone() {
        Ad ad = adRepository.save(new Ad("Old ad", "From before login"));

        assertThatThrownBy(() -> adService.update(ad.getId(), new AdRequest("Mine", "Now"), ann))
                .isInstanceOf(UnownedAdException.class);
        assertThatThrownBy(() -> adService.updateLegacy(ad.getId(), new AdRequest("Mine", "Now"), bob))
                .isInstanceOf(UnownedAdException.class);
    }

    @Test
    void anonymousCallerCannotCreateOrEdit() {
        Ad ad = adRepository.save(new Ad("Bike", "Old", ann.accountId()));

        assertThatThrownBy(() -> adService.create(new AdRequest("Bike", "New"), CurrentUser.anonymous()))
                .isInstanceOf(NotAuthenticatedException.class);
        assertThatThrownBy(() -> adService.update(ad.getId(), new AdRequest("X", "Y"), CurrentUser.anonymous()))
                .isInstanceOf(NotAuthenticatedException.class);
        assertThat(adRepository.count()).isEqualTo(1);
    }

    @Test
    void missingAdIsNotFoundBeforeOwnershipIsChecked() {
        assertThatThrownBy(() -> adService.update(999, new AdRequest("X", "Y"), bob))
                .isInstanceOf(AdNotFoundException.class);
    }

    @Test
    void editNeverChangesTheOwner() {
        Ad ad = adRepository.save(new Ad("Bike", "Old", ann.accountId()));

        adService.update(ad.getId(), new AdRequest("Red bike", "New"), ann);

        assertThat(adRepository.findById(ad.getId()).orElseThrow().getOwnerId()).isEqualTo(ann.accountId());
    }

    @Test
    void listCarriesAuthorNamesAndUnknownForUnownedAds() {
        adRepository.save(new Ad("Unowned", "Old"));
        adRepository.save(new Ad("Ann's", "A", ann.accountId()));
        adRepository.save(new Ad("Bob's", "B", bob.accountId()));

        List<AdView> ads = adService.findAll(CurrentUser.anonymous());

        assertThat(ads).extracting(AdView::title, AdView::authorName)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Unowned", "Unknown"),
                        org.assertj.core.groups.Tuple.tuple("Ann's", "Ann"),
                        org.assertj.core.groups.Tuple.tuple("Bob's", "Bob"));
    }

    @Test
    void ownedByMeIsTrueOnlyForTheOwner() {
        Ad ad = adRepository.save(new Ad("Ann's", "A", ann.accountId()));

        assertThat(adService.findById(ad.getId(), ann).ownedByMe()).isTrue();
        assertThat(adService.findById(ad.getId(), bob).ownedByMe()).isFalse();
        assertThat(adService.findById(ad.getId(), CurrentUser.anonymous()).ownedByMe()).isFalse();
    }

    @Test
    void listLooksUpPosterNamesInOneBatchedCall() {
        for (int i = 0; i < 5; i++) {
            adRepository.save(new Ad("Ann " + i, "A", ann.accountId()));
            adRepository.save(new Ad("Bob " + i, "B", bob.accountId()));
        }
        clearInvocations(accountService);

        assertThat(adService.findAll(ann)).hasSize(10);

        verify(accountService, times(1)).displayNames(anyCollection());
    }

    private UserAccount saveAccount(String email, String displayName) {
        return accountRepository.save(new UserAccount(email, displayName, "$2a$12$notARealHashButLongEnoughForTheColumn",
                Instant.parse("2026-10-20T10:00:00Z")));
    }
}
