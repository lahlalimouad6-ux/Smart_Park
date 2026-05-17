package com.smartpark.seed;

import com.smartpark.models.Parking;
import com.smartpark.models.ParkingCamera;
import com.smartpark.repository.ParkingCameraRepository;
import com.smartpark.repository.ParkingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class DatasetParkingSeeder implements CommandLineRunner {
    @Autowired
    private ParkingRepository parkingRepository;

    @Autowired
    private ParkingCameraRepository parkingCameraRepository;

    @Override
    public void run(String... args) throws Exception {
        Path datasetDir = Path.of(System.getProperty("user.dir")).resolve("..").resolve("datasets").resolve("parking").normalize();
        if (!Files.isDirectory(datasetDir)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(datasetDir, "*.mp4")) {
            for (Path video : stream) {
                String fileName = video.getFileName().toString();
                String label = fileName.replace(".mp4", "").replace("_", " ").trim();
                String parkingName = "Parking Caméra - " + label;

                Parking parking = parkingRepository.findByNom(parkingName).orElseGet(() -> {
                    Parking p = new Parking();
                    p.setNom(parkingName);
                    p.setAdresse("Dataset (caméra)");
                    p.setVille("Dataset");
                    p.setCoordGps("48.8566,2.3522");
                    p.setTarifHeure(new BigDecimal("3.50"));
                    return parkingRepository.save(p);
                });

                parkingCameraRepository.findByParkingId(parking.getId()).orElseGet(() -> {
                    ParkingCamera cam = new ParkingCamera();
                    cam.setParking(parking);
                    cam.setVideoFile(fileName);
                    return parkingCameraRepository.save(cam);
                });
            }
        }
    }
}
