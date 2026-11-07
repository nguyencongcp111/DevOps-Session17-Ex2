package com.storex.order.repository;

import com.storex.order.entity.StoreOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreOrderRepository extends JpaRepository<StoreOrder, Long> {

    List<StoreOrder> findAllByOrderByIdDesc();
}
