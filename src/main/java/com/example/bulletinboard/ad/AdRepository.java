package com.example.bulletinboard.ad;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdRepository extends JpaRepository<Ad, Long> {

    List<Ad> findAllByOrderByCreatedAtDesc();
}
