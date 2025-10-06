package BatterySwapStation.repository;

import BatterySwapStation.entity.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StationRepository extends JpaRepository<Station, Integer> {

    @Query(value = """
        SELECT 
            temp.stationid, 
            temp.stationname, 
            temp.address, 
            temp.latitude, 
            temp.longitude, 
            temp.isactive, 
            temp.distance
        FROM (
            SELECT 
                s.stationid,
                s.stationname,
                s.address,
                s.latitude,
                s.longitude,
                s.isactive,
                (
                    6371 * acos(
                        cos(radians(:lat)) * cos(radians(s.latitude))
                        * cos(radians(s.longitude) - radians(:lng))
                        + sin(radians(:lat)) * sin(radians(s.latitude))
                    )
                ) AS distance
            FROM station s
            WHERE s.isactive = true
        ) AS temp
        WHERE temp.distance <= :radiusKm
        ORDER BY temp.distance ASC
        """, nativeQuery = true)
    List<Object[]> findNearbyStations(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusKm") double radiusKm
    );
}
