package com.sporya.club.infrastructure.persistence;

import com.sporya.club.domain.Club;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClubRepository extends JpaRepository<Club, UUID> {}
