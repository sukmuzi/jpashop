package jpabook.jpashop.api;

import jpabook.jpashop.api.response.Result;
import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryDto;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.*;

/**
 * 쿼리 방식 선택 권장 순서
 * 1. 우선 엔티티를 DTO 로 변환하는 방법을 선택한다.
 * 2. 필요하면 페치 조인으로 성능을 최적화 한다. 대부분의 성능 이슈가 해결된다.
 * 3. 그래도 안되면 DTO 로 직접 조회하는 방법을 사용한다.
 * 4. 최후의 방법은 JPA 가 제공하는 네이티브 SQL 이나 스프링 JDBC Template 을 사용해서 SQL 을 직접 사용한다.
 */

/**
 * xToOne (ManyToOne, OneToOne)
 * Order
 * Order -> Member
 * Order -> Delivery
 * 주문 + 배송정보 + 회원 조회 API
 */
@RestController
@RequiredArgsConstructor
public class OrderSimpleApiController {

    private final OrderRepository orderRepository;
    private final OrderSimpleQueryRepository orderSimpleQueryRepository;

    /**
     * 주문 조회 V1
     * 엔티티를 직접 노출
     * - Hibernate5Module 모듈 등록, LAZY=null 처리
     * - 양방향 관계 문제 발생 -> @JsonIgnore
     * - 양쪽을 서로 호출하면서 무한 루프에 걸림.
     * order member 와 order address 는 지연 로딩이다. 따라서 실제 엔티티 대신에 프록시 존재
     * jackson 라이브러리는 기본적으로 이 프록시 객체를 json 으로 어떻게 생성해야 하는지 모름. 예외 발생
     * Hibernate5Module 을 스프링 빈으로 등록하면 해결 (스프링 부트 사용중)
     */
    @GetMapping("/api/v1/simple-orders")
    public List<Order> ordersV1() {
        List<Order> all = orderRepository.findAllByJpql(new OrderSearch());
        for (Order order : all) {
            order.getMember().getName();  // LAZY 강제 초기화
            order.getDelivery().getAddress();  // LAZY 강제 초기화
        }

        return all;
    }

    /**
     * 주문 조회 V2
     * 엔티티를 DTO 로 변환하는 일반적인 방법이다.
     * 쿼리가 총 1 + N + N번 실행된다. (v1과 쿼리수 결과는 같다.)
     * order 조회 1번(order 조회 결과 수가 N이 된다.)
     * order -> member 지연 로딩 조회 N 번
     * order -> delivery 지연 로딩 조회 N 번
     * 예) order 의 결과가 4개면 최악의 경우 1 + 4 + 4번 실행된다.(최악의 경우)
     * 지연로딩은 영속성 컨텍스트에서 조회하므로, 이미 조회된 경우 쿼리를 생략한다.
     */
    @GetMapping("/api/v2/simple-orders")
    public Result ordersV2() {
        List<Order> orders = orderRepository.findAllByJpql(new OrderSearch());
        List<SimpleOrderDto> collect = orders.stream()
                .map(o -> new SimpleOrderDto(o))
                .collect(toList());

        return new Result(collect);
    }

    /**
     * 주문 조회 V3
     * 엔티티를 DTO 로 변환 - 페치 조인 최적화
     * - fetch join 으로 쿼리 1번 호출
     * - 엔티티를 페치 조인(fetch join)을 사용해서 쿼리 1번에 조회
     * - 페치 조인으로 order -> member , order -> delivery 는 이미 조회 된 상태 이므로 지연로딩 X
     */
    @GetMapping("/api/v3/simple-orders")
    public Result ordersV3() {
        List<Order> orders = orderRepository.findAllWithMemberDelivery();
        List<SimpleOrderDto> collect = orders.stream()
                .map(o -> new SimpleOrderDto(o))
                .collect(toList());

        return new Result(collect);
    }

    /**
     * 주문 조회 V4
     * JPA 에서 DTO 로 바로 조회
     * 일반적인 SQL 을 사용할 때 처럼 원하는 값을 선택해서 조회
     * new 명령어를 사용해서 JPQL 의 결과를 DTO 로 즉시 변환
     * SELECT 절에서 원하는 데이터를 직접 선택하므로 DB 애플리케이션 네트웍 용량 최적화(생각보다 미비)
     * 리포지토리 재사용성 떨어짐, API 스펙에 맞춘 코드가 리포지토리에 들어가는 단점
     */
    @GetMapping("/api/v4/simple-orders")
    public Result ordersV4() {
        List<OrderSimpleQueryDto> orders = orderSimpleQueryRepository.findOrderDtos();

        return new Result(orders);
    }

    @Getter
    static class SimpleOrderDto {
        private final Long orderId;
        private final String name;
        private final LocalDateTime orderDate;
        private final OrderStatus orderStatus;
        private final Address address;

        public SimpleOrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress();
        }
    }
}
