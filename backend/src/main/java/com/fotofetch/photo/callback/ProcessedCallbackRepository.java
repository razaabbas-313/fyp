package com.fotofetch.photo.callback;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessedCallbackRepository extends JpaRepository<ProcessedCallback, String> {
}
