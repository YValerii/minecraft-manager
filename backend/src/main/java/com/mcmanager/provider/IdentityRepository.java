package com.mcmanager.provider;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityRepository extends JpaRepository<IdentityEntity, String> {
}
