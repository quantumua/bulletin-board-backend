package com.example.bulletinboard.ad;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdRepository extends JpaRepository<Ad, Long> {

    @EntityGraph(attributePaths = "owner")
    List<Ad> findAllByOrderByCreatedAtDesc();

    @Override
    @EntityGraph(attributePaths = "owner")
    Optional<Ad> findById(Long id);
}
