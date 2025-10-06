package BatterySwapStation.service;

import BatterySwapStation.dto.StationResponseDTO;
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

        // ✅ Tổng hợp số lượng pin theo trạng thái
        Map<String, Long> batterySummary = Optional.ofNullable(station.getDocks())
                .orElse(Collections.emptySet()) // dùng emptySet cho Set<>
                .stream()
                .flatMap(dock -> Optional.ofNullable(dock.getDockSlots())
                        .orElse(Collections.emptySet())
                        .stream())
                .filter(slot -> slot.getBattery() != null)
                .collect(Collectors.groupingBy(
                        slot -> slot.getBattery().getBatteryStatus().toString(),
                        Collectors.counting()
                ));

        // ✅ Tổng hợp số lượng pin theo loại
        Map<String, Long> batteryTypes = Optional.ofNullable(station.getDocks())
                .orElse(Collections.emptySet())
                .stream()
                .flatMap(dock -> Optional.ofNullable(dock.getDockSlots())
                        .orElse(Collections.emptySet())
                        .stream())
                .filter(slot -> slot.getBattery() != null && slot.getBattery().getBatteryType() != null)
                .collect(Collectors.groupingBy(
                        slot -> slot.getBattery().getBatteryType().toString(),
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
                null // distanceKm set ở chỗ khác
        );
    }

}
