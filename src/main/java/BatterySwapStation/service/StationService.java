package BatterySwapStation.service;

import BatterySwapStation.dto.StationResponseDTO;
import BatterySwapStation.entity.Station;
import BatterySwapStation.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationService {

    private final StationRepository stationRepository;

    public List<StationResponseDTO> getAllStations() {
        return stationRepository.findAll()
                .stream().map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public StationResponseDTO getStationDetail(int id) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Station not found"));
        return mapToDTO(station);
    }


// lay danh sach tram trong vung lân cận
    public List<StationResponseDTO> getNearbyStations(double lat, double lng, double radiusKm) {
        if (radiusKm <= 0) radiusKm = 50;

        // Gọi query từ repository
        List<Object[]> results = stationRepository.findNearbyStations(lat, lng, radiusKm);

        // Danh sách kết quả
        List<StationResponseDTO> nearby = new ArrayList<>();

        // Duyệt từng dòng trả về từ SQL
        for (Object[] row : results) {
            Station s = new Station();
            s.setStationId(((Number) row[0]).intValue());
            s.setStationName((String) row[1]);
            s.setAddress((String) row[2]);
            s.setLatitude(new BigDecimal(String.valueOf(row[3])));
            s.setLongitude(new BigDecimal(String.valueOf(row[4])));
            s.setActive((Boolean) row[5]);   // chú ý: phải dùng setIsActive()

            double distance = ((Number) row[6]).doubleValue();
            StationResponseDTO dto = mapToDTO(s);
            dto.setDistanceKm(distance);
            nearby.add(dto);
        }

        return nearby;
    }


    private StationResponseDTO mapToDTO(Station station) {
        Map<String, Long> batterySummary = Optional.ofNullable(station.getDocks())
                .orElse(List.of())
                .stream()
                .flatMap(dock -> dock.getDockSlots().stream())
                .filter(slot -> slot.getBattery() != null)
                .collect(Collectors.groupingBy(
                        slot -> slot.getBattery().getBatteryStatus().toString(),
                        Collectors.counting()
                ));

        Map<String, Long> batteryTypes = Optional.ofNullable(station.getDocks())
                .orElse(List.of())
                .stream()
                .flatMap(dock -> dock.getDockSlots().stream())
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
                null
        );
    }
}
