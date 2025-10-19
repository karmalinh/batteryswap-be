package BatterySwapStation.repository;

import BatterySwapStation.entity.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StationRepository extends JpaRepository<Station, Integer> {

    @Query(value = """
SELECT 
    s.station_id,
    s.name AS station_name,
    s.address,
    s.latitude,
    s.longitude,
    s.is_active,
    b.battery_type,
    b.status AS battery_status,
    COUNT(b.battery_id) AS count
FROM station s
JOIN dock d ON d.station_id = s.station_id
JOIN dockslot ds ON ds.dock_id = d.dock_id
LEFT JOIN battery b ON b.battery_id = ds.battery_id
WHERE b.battery_id IS NOT NULL
GROUP BY s.station_id, s.name, s.address, s.latitude, s.longitude, s.is_active, b.battery_type, b.status
ORDER BY s.station_id
""", nativeQuery = true)
    List<Object[]> getStationBatteryStats();



}
