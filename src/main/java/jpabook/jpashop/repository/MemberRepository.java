package jpabook.jpashop.repository;

import jpabook.jpashop.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA 로 변경
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    /**
     * 메서드 이름이 중요함.
     * select m from Member m where m.name = :name
     * 이런식으로 메서드 이름으로 정환한 JPQL 쿼리를 실행한다.
     */
    List<Member> findByName(String name);
}
