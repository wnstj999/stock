package com.mock.stock.domain.trade.repository;

import com.mock.stock.domain.trade.entity.TradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeHistoryRepository extends JpaRepository<TradeHistory, Long> {
    List<TradeHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
}
