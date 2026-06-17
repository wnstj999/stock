package com.mock.stock.domain.trade.repository;

import com.mock.stock.domain.trade.entity.UserStock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserStockRepository extends JpaRepository<UserStock, Long> {
    Optional<UserStock> findByUserIdAndStockId(Long userId, Long stockId);
    List<UserStock> findByUserId(Long userId);
}
