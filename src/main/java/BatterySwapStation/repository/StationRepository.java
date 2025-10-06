package BatterySwapStation.repository;

import BatterySwapStation.entity.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StationRepository extends JpaRepository<Station, Integer> {

    @Query("""
        SELECT DISTINCT s FROM Station s
        LEFT JOIN FETCH s.docks d
        LEFT JOIN FETCH d.dockSlots ds
        LEFT JOIN FETCH ds.battery b
        WHERE s.isActive = true
    """)
    List<Station> findAllWithBatteryDetails();
}
