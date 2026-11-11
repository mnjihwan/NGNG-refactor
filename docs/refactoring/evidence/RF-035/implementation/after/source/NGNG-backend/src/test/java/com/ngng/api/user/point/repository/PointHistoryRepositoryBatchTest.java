package com.ngng.api.user.point.repository;

import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import com.ngng.api.user.point.payment.PaymentFixture;


import com.ngng.api.user.point.entity.PointHistory;
import com.ngng.api.user.point.service.PointHistoryService;
import com.ngng.api.user.user.entity.Role;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.AuthService;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Offline query validation and delegation only; does not verify MySQL query results or SQL. */
class PointHistoryRepositoryBatchTest {
    @Test
    void batchQueryResolvesEntityPathsWithExistingHibernateWithoutConnectingToDatabase() throws Exception {
        String hql = PointHistoryRepository.class.getMethod("findLatestByUserIds", List.class)
                .getAnnotation(Query.class).value();
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", "false")
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .applySetting("hibernate.connection.provider_class",
                        "org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl")
                .build();
        try (SessionFactoryImplementor sessionFactory = (SessionFactoryImplementor) new MetadataSources(registry)
                .addAnnotatedClass(User.class)
                .addAnnotatedClass(Role.class)
                .addAnnotatedClass(PointHistory.class)
                .buildMetadata().buildSessionFactory()) {
            assertThatCode(() -> sessionFactory.getQueryEngine().getHqlTranslator()
                    .translate(hql, PointHistory.class)).doesNotThrowAnyException();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void emptyUserIdsDoNotReachRepository() {
        PointHistoryRepository repository = mock(PointHistoryRepository.class);
        AuthService auth = mock(AuthService.class);
        PointHistoryService service = new PointHistoryService(repository, auth, mock(UserRepository.class), mock(ProductRepository.class), mock(TransactionDetailsRepository.class));

        assertThat(service.readCostsByUserIds(List.of())).isEmpty();

        verifyNoInteractions(repository, auth);
    }

    @Test
    void batchLookupPassesOnlyRequestedIdsAndLeavesSingleUserLookupUnchanged() {
        PointHistoryRepository repository = mock(PointHistoryRepository.class);
        AuthService auth = mock(AuthService.class);
        PointHistoryService service = new PointHistoryService(repository, auth, mock(UserRepository.class), mock(ProductRepository.class), mock(TransactionDetailsRepository.class));
        User user = new User(7L);
        PointHistory latest = PointHistory.builder().id(109L).user(user).cost(15L).build();
        List<Long> ids = List.of(42L, 7L);
        List<PointHistory> histories = List.of(latest);
        when(repository.findLatestByUserIds(ids)).thenReturn(histories);
        when(repository.findLastByUserId(7L)).thenReturn(java.util.Optional.of(latest));

        assertThat(service.readCostsByUserIds(ids)).isSameAs(histories);
        assertThat(service.readCostByUser(user)).isSameAs(latest);

        verify(repository).findLatestByUserIds(ids);
        verify(repository).findLastByUserId(7L);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(auth);
    }
}
