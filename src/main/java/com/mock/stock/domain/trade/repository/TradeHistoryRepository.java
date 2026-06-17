package com.mock.stock.domain.trade.repository;

import com.mock.stock.domain.trade.entity.TradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeHistoryRepository extends JpaRepository<TradeHistory, Long> {
}
