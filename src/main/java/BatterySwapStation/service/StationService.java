package BatterySwapStation.service;

import BatterySwapStation.dto.StationResponseDTO;
import BatterySwapStation.entity.Battery;
import BatterySwapStation.entity.Dock;
import BatterySwapStation.entity.DockSlot;
import BatterySwapStation.entity.Station;
import BatterySwapStation.repository.StationRepository;
import BatterySwapStation.utils.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationService {

    private final StationRepository stationRepository;

    /**
     * ⚡ Cache 5 phút: tất cả trạm kèm dock + battery
     */
    @Cacheable("stations")
    public List<Station> getAllActiveStations() {
        System.out.println("⏳ Loading stations from DB...");
        return stationRepository.findAllWithBatteryDetails();
    }

    /**
     * 📍 Lấy toàn bộ trạm (dùng cache sẵn có)
     */
    public List<StationResponseDTO> getAllStations() {
        return getAllActiveStations().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 📍 Lấy chi tiết trạm theo ID
     */
    public StationResponseDTO getStationDetail(int id) {
        return getAllActiveStations().stream()
                .filter(s -> s.getStationId() == id)
                .findFirst()
                .map(this::mapToDTO)
                .orElseThrow(() -> new RuntimeException("Station not found: " + id));
    }

    /**
     * 🔍 Lấy danh sách trạm trong bán kính radiusKm (km)
     */
    public List<StationResponseDTO> getNearbyStations(double lat, double lng, double radiusKm) {
        // ✅ 1. Dùng biến final riêng để tránh lỗi lambda
        final double radius = (radiusKm <= 0) ? 50 : radiusKm;

        // ✅ 2. Lấy tất cả trạm từ cache (đã bao gồm battery, dock)
        List<Station> allStations = getAllActiveStations();

        // ✅ 3. Tính khoảng cách, lọc theo bán kính, sắp xếp tăng dần
        return allStations.stream()
                .map(st -> {
                    double distance = GeoUtils.haversineKm(
                            lat, lng,
                            st.getLatitude().doubleValue(),
                            st.getLongitude().doubleValue()
                    );
                    StationResponseDTO dto = mapToDTO(st);
                    dto.setDistanceKm(distance);
                    return dto;
                })
                .filter(dto -> dto.getDistanceKm() <= radius)  // sử dụng biến final radius
                .sorted(Comparator.comparingDouble(StationResponseDTO::getDistanceKm))
                .collect(Collectors.toList());
    }

    private StationResponseDTO mapToDTO(Station station) {
        Set<String> seen = new HashSet<>();

        // ⚠️ bỏ .filter(Battery::isActive) để vẫn đếm được DAMAGED
        List<Battery> bats = Optional.ofNullable(station.getDocks())
                .orElseGet(Collections::emptySet)
                .stream()
                .filter(Objects::nonNull)
                .filter(Dock::isActive)                    // giữ lọc dock active
                .flatMap(d -> Optional.ofNullable(d.getDockSlots())
                        .orElseGet(Collections::emptySet).stream())
                .filter(Objects::nonNull)
                .filter(DockSlot::isActive)                // giữ lọc slot active
                .map(DockSlot::getBattery)
                .filter(Objects::nonNull)
                .filter(b -> b.getBatteryId()!=null && seen.add(b.getBatteryId())) // khử trùng
                .collect(Collectors.toList());

        // ✅ Summary: chỉ 3 trạng thái, bỏ IN_USE
        Map<String, Long> batterySummary = bats.stream()
                .filter(b -> b.getBatteryStatus()!=null)
                .filter(b -> b.getBatteryStatus()==Battery.BatteryStatus.AVAILABLE
                        || b.getBatteryStatus()==Battery.BatteryStatus.CHARGING
                        || b.getBatteryStatus()==Battery.BatteryStatus.DAMAGED)
                .collect(Collectors.groupingBy(
                        b -> b.getBatteryStatus().name(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        // ✅ Types: chỉ đếm loại pin có thể dùng (AVAILABLE + CHARGING)
        Map<String, Long> batteryTypes = bats.stream()
                .filter(b -> b.getBatteryStatus()==Battery.BatteryStatus.AVAILABLE
                        || b.getBatteryStatus()==Battery.BatteryStatus.CHARGING)
                .filter(b -> b.getBatteryType()!=null)
                .collect(Collectors.groupingBy(
                        b -> b.getBatteryType().name(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        return new StationResponseDTO(
                station.getStationId(),
                station.getStationName(),
                station.getAddress(),
                station.getLatitude(),
                station.getLongitude(),
                station.isActive(),
                batterySummary,
                batteryTypes,
                null
        );
    }
}
