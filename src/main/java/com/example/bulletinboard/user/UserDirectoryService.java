package com.example.bulletinboard.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserDirectoryService {

    private final UserRepository repository;

    public UserDirectoryService(UserRepository repository) {
        this.repository = repository;
    }

    public List<UserDirectoryEntry> findAll() {
        return repository.findAllByOrderByCreatedAtDescIdDesc().stream().map(UserDirectoryEntry::from).toList();
    }
}
