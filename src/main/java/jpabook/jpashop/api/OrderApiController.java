package jpabook.jpashop.api;

import jpabook.jpashop.api.response.Result;
import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderItem;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.query.OrderFlatDto;
import jpabook.jpashop.repository.order.query.OrderItemQueryDto;
import jpabook.jpashop.repository.order.query.OrderQueryDto;
import jpabook.jpashop.repository.order.query.OrderQueryRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.*;

/**
 * V1. 엔티티 직접 노출
 * - 엔티티가 변하면 API 스펙이 변한다.
 * - 트랜잭션 안에서 지연 로딩 필요
 * - 양방향 연관관계 문제
 *
 * V2. 엔티티를 조회해서 DTO 로 변환(fetch join 사용X)
 * - 트랜잭션 안에서 지연 로딩 필요
 * V3. 엔티티를 조회해서 DTO 로 변환(fetch join 사용O)
 * - 페이징 시에는 N 부분을 포기해야함(대신에 batch fetch size 옵션 주면 N -> 1 쿼리로 변경 가능)
 *
 * V4. JPA 에서 DTO 로 바로 조회, 컬렉션 N 조회 (1 + N Query)
 * - 페이징 가능
 * V5. JPA 에서 DTO 로 바로 조회, 컬렉션 1 조회 최적화 버전 (1 + 1 Query)
 * - 페이징 가능
 * V6. JPA 에서 DTO 로 바로 조회, 플랫 데이터(1 Query) (1 Query)
 * - 페이징 불가능
 */

/**
 * 1. 엔티티 조회 방식으로 우선 접근
 *      1.1. 페치조인으로 쿼리 수를 최적화
 *      1.2. 컬렉션 최적화
 *          1.2.1 페이징 필요 hibernate.default_batch_fetch_size , @BatchSize 로 최적화
 *          1.2.2 페이징 필요X 페치 조인 사용
 * 2. 엔티티 조회 방식으로 해결이 안되면 DTO 조회 방식 사용
 * 3. DTO 조회 방식으로 해결이 안되면 NativeSQL or 스프링 JdbcTemplate
 */
@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;

    /**
     * V1. 엔티티 직접 노출
     * - Hibernate5Module 모듈 등록, LAZY=null 처리
     * - 양방향 관계 문제 발생 -> @JsonIgnore
     */
    @GetMapping("/api/v1/orders")
    public List<Order> ordersV1() {
        List<Order> orders = orderRepository.findAllByJpqlString(new OrderSearch());
        for (Order order : orders) {
            order.getMember().getName();
            order.getDelivery().getAddress();

            List<OrderItem> orderItems = order.getOrderItems();
            orderItems.stream().forEach(o -> o.getItem().getName());
        }

        return orders;
    }

    /**
     * V2. 지연 로딩으로 너무 많은 SQL 실행
     * SQL 실행 수
     * order 1번
     * member , address N번(order 조회 수 만큼)
     * orderItem N번(order 조회 수 만큼)
     * item N번(orderItem 조회 수 만큼)
     */
    @GetMapping("/api/v2/orders")
    public Result<OrderDto> ordersV2() {
        List<Order> orders = orderRepository.findAllByJpqlString(new OrderSearch());
        List<OrderDto> collect = orders.stream()
                .map(OrderDto::new)
                .collect(toList());

        return new Result(collect);
    }

    /**
     * V3. 페치 조인으로 SQL 이 1번만 실행됨
     * distinct 를 사용한 이유는 1대다 조인이 있으므로 데이터베이스 row 가 증가한다. 그 결과 같은 order
     * 엔티티의 조회 수도 증가하게 된다. JPA 의 distinct 는 SQL 에 distinct 를 추가하고, 더해서 같은 엔티티가
     * 조회되면, 애플리케이션에서 중복을 걸러준다. 이 예에서 order 가 컬렉션 페치 조인 때문에 중복 조회 되는 것을 막아준다.
     * 단점
     * - 페이징 불가능
     *
     * 참고
     * - 컬렉션 페치 조인을 사용하면 페이징이 불가능하다. 하이버네이트는 경고 로그를 남기면서 모든 데이터를 DB 에서 읽어오고,
     *   메모리에서 페이징 해버린다(매우 위험하다).
     * - 컬렉션 페치 조인은 1개만 사용할 수 있다. 컬렉션 둘 이상에 페치 조인을 사용하면 안된다. 데이터가 부정합하게 조회될 수 있다.
     */
    @GetMapping("/api/v3/orders")
    public Result<OrderDto> ordersV3() {
        List<Order> orders = orderRepository.findAllWithItem();
        List<OrderDto> collect = orders.stream()
                .map(OrderDto::new)
                .collect(toList());

        return new Result(collect);
    }

    /**
     * V3.1 페이징 고려
     * 먼저 ToOne(OneToOne, ManyToOne) 관계를 모두 페치조인 한다. ToOne 관계는 row 수를 증가시키지 않으므로 페이징 쿼리에 영향을 주지 않는다.
     * 컬렉션은 지연 로딩으로 조회한다.
     * 지연 로딩 성능 최적화를 위해 hibernate.default_batch_fetch_size , @BatchSize 를 적용한다.
     * hibernate.default_batch_fetch_size: 글로벌 설정
     * @BatchSize: 개별 최적화
     * 이 옵션을 사용하면 컬렉션이나, 프록시 객체를 한꺼번에 설정한 size 만큼 IN 쿼리로 조회한다.
     * 장점
     * - 쿼리 호출 수가 1 + N -> 1 + 1 로 최적화 된다.
     * - 조인보다 DB 데이터 전송량이 최적화 된다. (Order 와 OrderItem 을 조인하면 Order 가 OrderItem 만큼 중복해서 조회된다.
     * - 이 방법은 각각 조회하므로 전송해야할 중복 데이터가 없다.)
     * - 페치 조인 방식과 비교해서 쿼리 호출 수가 약간 증가하지만, DB 데이터 전송량이 감소한다.
     * - 컬렉션 페치 조인은 페이징이 불가능 하지만 이 방법은 페이징이 가능하다.
     * 결론
     * - ToOne 관계는 페치 조인해도 페이징에 영향을 주지 않는다. 따라서 ToOne 관계는 페치조인으로
     * - 쿼리 수를 줄여 해결하고, 나머지는 hibernate.default_batch_fetch_size 로 최적화 하자.
     */
    @GetMapping("/api/v3.1/orders")
    public Result<OrderDto> ordersV3_1(
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        List<Order> orders = orderRepository.findAllWithMemberDelivery(offset, limit);


        List<OrderDto> collect = orders.stream()
                .map(OrderDto::new)
                .collect(toList());

        return new Result(collect);
    }

    /**
     * V4. Query: 루트 1번, 컬렉션 N 번 실행
     * ToOne(N:1, 1:1) 관계들을 먼저 조회하고, ToMany(1:N) 관계는 각각 별도로 처리한다.
     * 이런 방식을 선택한 이유는 다음과 같다.
     * ToOne 관계는 조인해도 데이터 row 수가 증가하지 않는다.
     * ToMany(1:N) 관계는 조인하면 row 수가 증가한다.
     * row 수가 증가하지 않는 ToOne 관계는 조인으로 최적화 하기 쉬우므로 한번에 조회하고,
     * ToMany 관계는 최적화 하기 어려우므로 findOrderItems() 같은 별도의 메서드로 조회한다.
     */
    @GetMapping("/api/v4/orders")
    public Result<OrderQueryDto> ordersV4() {
        List<OrderQueryDto> orders = orderQueryRepository.findOrderQueryDtos();

        return new Result(orders);
    }

    /**
     * V5. JPA 에서 DTO 로 바로 조회, 컬렉션 1 조회 최적화 버전 (1 + 1 Query)
     */
    @GetMapping("/api/v5/orders")
    public Result<OrderQueryDto> ordersV5() {
        List<OrderQueryDto> orders = orderQueryRepository.findAllByDtoOptimization();

        return new Result(orders);
    }

    /**
     * V6: JPA 에서 DTO 로 직접 조회, 플랫 데이터 최적화
     * Query 1번 발생
     * 단점
     * - 쿼리는 한번이지만 조인으로 인해 DB 에서 애플리케이션에 전달하는 데이터에 중복 데이터가 추가되므로 상황에 따라 V5 보다 더 느릴 수 도 있다.
     * - 애플리케이션에서 추가 작업이 크다.
     * - 페이징 불가능
     */
    @GetMapping("/api/v6/orders")
    public Result<OrderQueryDto> ordersV6() {
        List<OrderFlatDto> flats = orderQueryRepository.findAllByDtoFlat();

        List<OrderQueryDto> orders = flats.stream()
                .collect(groupingBy(o -> new OrderQueryDto(o.getOrderId(), o.getName(), o.getOrderDate(), o.getOrderStatus(), o.getAddress()),
                        mapping(o -> new OrderItemQueryDto(o.getOrderId(), o.getItemName(), o.getOrderPrice(), o.getCount()), toList())
                )).entrySet().stream()
                .map(e -> new OrderQueryDto(e.getKey().getOrderId(), e.getKey().getName(), e.getKey().getOrderDate(), e.getKey().getOrderStatus(),
                        e.getKey().getAddress(), e.getValue()))
                .sorted(Comparator.comparing(OrderQueryDto::getOrderId)) // orderId 오름차순 정렬 (필요없음)
                .collect(toList());

        return new Result(orders);
    }

    @Getter
    static class OrderDto {
        private final Long orderId;
        private final String name;
        private final LocalDateTime orderDate;
        private final OrderStatus orderStatus;
        private final Address address;
        private final List<OrderItemDto> orderItems;

        // DTO 에선 엔티티를 참조해도 괜찮다.
        public OrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress();
            orderItems = order.getOrderItems().stream()
                    .map(OrderItemDto::new)
                    .collect(toList());
        }
    }

    @Getter
    static class OrderItemDto {
        private final String itemName;
        private final int orderPrice;
        private final int count;

        public OrderItemDto(OrderItem orderItem) {
            itemName = orderItem.getItem().getName();
            orderPrice = orderItem.getOrderPrice();
            count = orderItem.getCount();
        }
    }
}
