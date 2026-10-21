package com.example.bulletinboard.ad;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdRepository extends JpaRepository<Ad, Long> {

    // Newest first; id breaks ties between ads created in the same instant so the order is stable
    List<Ad> findAllByOrderByCreatedAtDescIdDesc();
}
