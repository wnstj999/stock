package com.mock.stock.domain.trade.repository;

import com.mock.stock.domain.trade.entity.Position;
import com.mock.stock.domain.trade.entity.PositionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {
    List<Position> findByUserId(Long userId);
    Optional<Position> findByUserIdAndStockIdAndPositionType(Long userId, Long stockId, PositionType positionType);
    List<Position> findByUserIdAndStockId(Long userId, Long stockId);
}
