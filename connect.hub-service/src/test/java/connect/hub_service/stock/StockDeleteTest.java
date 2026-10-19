package connect.hub_service.stock;

import connect.hub_service.stock.entity.Stock;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Stock.delete 도메인 규칙 검증(DB 불필요)
class StockDeleteTest {

    @Test
    void 처음_삭제하면_true를_반환하고_삭제_시각과_삭제자를_기록하며_수량은_유지한다() {
        Stock stock = new Stock(null, UUID.randomUUID(), 90);
        UUID userId = UUID.randomUUID();
        LocalDateTime deletedAt = LocalDateTime.of(2026, 10, 16, 9, 30);

        boolean deleted = stock.delete(userId, deletedAt);

        assertThat(deleted).isTrue();
        assertThat(stock.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(stock.getDeletedBy()).isEqualTo(userId);
        assertThat(stock.getQuantity()).isEqualTo(90);
    }

    @Test
    void 이미_삭제된_재고는_false를_반환하고_삭제자와_시각을_바꾸지_않는다() {
        Stock stock = new Stock(null, UUID.randomUUID(), 90);
        UUID first = UUID.randomUUID();
        LocalDateTime firstAt = LocalDateTime.of(2026, 10, 16, 9, 30);
        stock.delete(first, firstAt);

        boolean deletedAgain = stock.delete(UUID.randomUUID(), firstAt.plusHours(1));

        assertThat(deletedAgain).isFalse();
        assertThat(stock.getDeletedBy()).isEqualTo(first);
        assertThat(stock.getDeletedAt()).isEqualTo(firstAt);
    }

    @Test
    void 삭제된_재고도_수량을_늘릴_수_있고_삭제_상태는_유지된다() {
        Stock stock = new Stock(null, UUID.randomUUID(), 90);
        UUID userId = UUID.randomUUID();
        LocalDateTime deletedAt = LocalDateTime.of(2026, 10, 16, 9, 30);
        stock.delete(userId, deletedAt);

        stock.increase(10);

        assertThat(stock.getQuantity()).isEqualTo(100);
        assertThat(stock.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(stock.getDeletedBy()).isEqualTo(userId);
    }

    @Test
    void 차감은_수량을_줄이고_부족하거나_1_미만이면_거절한다() {
        Stock stock = new Stock(null, UUID.randomUUID(), 5);

        stock.decrease(5);
        assertThat(stock.getQuantity()).isZero();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> stock.decrease(1))
                .isInstanceOf(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> stock.decrease(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(stock.getQuantity()).isZero();
    }
}
