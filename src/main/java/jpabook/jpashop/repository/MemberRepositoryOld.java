package jpabook.jpashop.repository;

import jpabook.jpashop.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MemberRepositoryOld {

    // spring data jpa 는 EntityManager 에 대해 @Autowired 를 제공하기 때문에 injection 가능
    // 그게 아니라면 @PersistenceContext 사용해야함.
    private final EntityManager em;

    public void save(Member member) {
        // persist 자체만으로는 db insert 가 발생하지 않는다. transaction commit 후 flush 될 때 insert 발생.
        em.persist(member);
    }

    // find(1, 2) 1: 반환 타입 2: PK
    // createQuery(1, 2) 1: query 2: 반환 타입 JPQL은 테이블 대상이 아닌 엔티티 대상으로 쿼리
    public Member findOne(Long id) {
        return em.find(Member.class, id);
    }

    public List<Member> findAll() {
        return em.createQuery("select m from Member m", Member.class)
                .getResultList();
    }

    public List<Member> findByName(String name) {
        return em.createQuery("select m from Member m where m.name = :name", Member.class)
                .setParameter("name", name)
                .getResultList();
    }
}
