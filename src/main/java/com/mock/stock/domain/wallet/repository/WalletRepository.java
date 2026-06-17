package com.mock.stock.domain.wallet.repository;

import com.mock.stock.domain.wallet.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
}
