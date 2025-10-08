package BatterySwapStation.service;

import BatterySwapStation.dto.StationResponseDTO;
import BatterySwapStation.repository.StationRepository;
import BatterySwapStation.utils.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationService {

    private final StationRepository stationRepository;

    // ⚡ Lấy toàn bộ trạm với tổng hợp nhanh
    public List<StationResponseDTO> getAllStations() {
        List<Object[]> main = stationRepository.getStationSummary();
        Map<Integer, List<Object[]>> typeMap = stationRepository.getStationBatteryTypes()
                .stream()
                .collect(Collectors.groupingBy(o -> (Integer) o[0]));

        List<StationResponseDTO> result = new ArrayList<>();

        for (Object[] row : main) {
            Integer id = (Integer) row[0];
            String name = (String) row[1];
            String address = (String) row[2];
            var lat = (java.math.BigDecimal) row[3];
            var lon = (java.math.BigDecimal) row[4];
            boolean isActive = (boolean) row[5];
            int available = ((Number) Optional.ofNullable(row[6]).orElse(0)).intValue();
            int charging = ((Number) Optional.ofNullable(row[7]).orElse(0)).intValue();
            int total = ((Number) Optional.ofNullable(row[8]).orElse(0)).intValue();

            // nhóm theo loại pin
            List<StationResponseDTO.BatteryTypeRow> batteryRows =
                    typeMap.getOrDefault(id, List.of()).stream()
                            .filter(o -> o[1] != null)
                            .map(o -> new StationResponseDTO.BatteryTypeRow(
                                    String.valueOf(o[1]),
                                    ((Number) Optional.ofNullable(o[2]).orElse(0)).intValue(),
                                    ((Number) Optional.ofNullable(o[3]).orElse(0)).intValue()
                            ))
                            .filter(bt -> bt.getTotal() > 0) // ⚡ bỏ luôn loại pin không có gì
                            .toList();


            result.add(StationResponseDTO.builder()
                    .stationId(id)
                    .stationName(name)
                    .address(address)
                    .latitude(lat)
                    .longitude(lon)
                    .isActive(isActive)
                    .availableCount(available)
                    .chargingCount(charging)
                    .totalBatteries(total)
                    .batteries(batteryRows)
                    .build());
        }
        return result;
    }

    public StationResponseDTO getStationDetail(int id) {
        return getAllStations().stream()
                .filter(s -> Objects.equals(s.getStationId(), id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Station not found: " + id));
    }

    // ⚡ API /nearby
    public List<StationResponseDTO> getNearbyStations(double lat, double lng, double radiusKm) {
        final double radius = radiusKm <= 0 ? 50 : radiusKm;
        return getAllStations().stream()
                .filter(st -> {
                    double distance = GeoUtils.haversineKm(
                            lat, lng,
                            st.getLatitude().doubleValue(),
                            st.getLongitude().doubleValue()
                    );
                    return distance <= radius;
                })
                .toList();
    }

    // ⚡ Lọc theo loại pin
    public List<StationResponseDTO> searchByBattery(String batteryType, int minTotal, int minAvailable) {
        List<Integer> ids = stationRepository.filterByBattery(batteryType, minTotal, minAvailable);
        Set<Integer> set = new HashSet<>(ids);
        return getAllStations().stream()
                .filter(s -> set.contains(s.getStationId()))
                .toList();
    }

    // ⚡ Lọc theo quận / thành phố
    public List<StationResponseDTO> searchByArea(String district, String city) {
        List<Integer> ids = stationRepository.filterByArea(district, city);
        Set<Integer> set = new HashSet<>(ids);
        return getAllStations().stream()
                .filter(s -> set.contains(s.getStationId()))
                .toList();
    }
}
