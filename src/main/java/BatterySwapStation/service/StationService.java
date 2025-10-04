package BatterySwapStation.service;

import BatterySwapStation.dto.StationResponseDTO;
import BatterySwapStation.entity.Station;
import BatterySwapStation.repository.StationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StationService {

    @Autowired
    private StationRepository stationRepository;

    public List<StationResponseDTO> getAllStations() {
        List<Station> stations = stationRepository.findAll();
        return stations.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public StationResponseDTO getStationDetail(int id) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Station not found"));
        return mapToDTO(station);
    }

    private StationResponseDTO mapToDTO(Station station) {
        // Tổng hợp theo trạng thái pin (AVAILABLE, IN_USE, CHARGING, DAMAGED)
        Map<String, Long> batterySummary = station.getDocks().stream()
                .flatMap(dock -> dock.getDockSlots().stream())
                .filter(slot -> slot.getBattery() != null)
                .collect(Collectors.groupingBy(
                        slot -> slot.getBattery().getBatteryStatus().toString(),
                        Collectors.counting()
                ));

        // Nếu chưa có BatteryType thì có thể dùng nhóm theo Active/Inactive
        Map<String, Long> batteryTypes = station.getDocks().stream()
                .flatMap(dock -> dock.getDockSlots().stream())
                .filter(slot -> slot.getBattery() != null)
                .collect(Collectors.groupingBy(
                        slot -> slot.getBattery().isActive() ? "Active" : "Inactive",
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
                batteryTypes
        );
    }
}
