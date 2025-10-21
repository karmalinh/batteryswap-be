package BatterySwapStation.repository;

import BatterySwapStation.entity.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Integer> {
}
