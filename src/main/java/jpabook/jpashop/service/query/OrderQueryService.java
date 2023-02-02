package jpabook.jpashop.service.query;

import org.springframework.transaction.annotation.Transactional;

/**
 * OSIV 를 껐을 때를 대비한 서비스 계층
 * OrderService
 * - OrderService: 핵심 비즈니스 로직
 * - OrderQueryService: 화면이나 API 에 맞춘 서비스 (주로 읽기 전용 트랜잭션 사용)
 *
 * 참고
 * 고객 서비스의 실시간 API 는 OSIV 를 끄고, ADMIN 처럼 커넥션을 많이 사용하지 않는 곳에서는 OSIV 를 키는 방향으로 ...
 */
@Transactional(readOnly = true)
public class OrderQueryService {
}
