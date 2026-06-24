package com.qarzbot.repository;

import com.qarzbot.entity.ShopRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopRequestRepository extends JpaRepository<ShopRequest, Long> {

    List<ShopRequest> findByStatus(ShopRequest.Status status);
}
