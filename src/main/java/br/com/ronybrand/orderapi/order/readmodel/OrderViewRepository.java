package br.com.ronybrand.orderapi.order.readmodel;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderViewRepository extends MongoRepository<OrderView, String> {
}
