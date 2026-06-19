package com.mock.stock.domain.trade.repository;

import com.mock.stock.domain.trade.entity.LimitOrder;
import com.mock.stock.domain.trade.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LimitOrderRepository extends JpaRepository<LimitOrder, Long> {
    List<LimitOrder> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderStatus status);
    List<LimitOrder> findByStatus(OrderStatus status);
}
